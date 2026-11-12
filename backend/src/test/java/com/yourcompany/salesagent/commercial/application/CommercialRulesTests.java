package com.yourcompany.salesagent.commercial.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.opportunity.application.OpportunityService;

class CommercialRulesTests {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"),ZoneOffset.UTC);
    private static final LocalDate TODAY=LocalDate.parse("2026-11-12");
    private static final UUID ORG=UUID.randomUUID(), MEMBER=UUID.randomUUID(), CUSTOMER=UUID.randomUUID();
    private static AuthPrincipal actor(String role) { return new AuthPrincipal(null,UUID.randomUUID(),ORG,MEMBER,"sales@test.local","Sales","Team",role,CLOCK.instant().plusSeconds(900)); }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private static ProductRow product(String price,String tax) { var p=new ProductRow(); p.setId(UUID.randomUUID()); p.setSku("SKU"); p.setName("Product"); p.setUnit("unit"); p.setActive(true); p.setListPrice(decimal(price)); p.setTaxRate(decimal(tax)); return p; }
    @Test void calculatesDiscountAndTaxPerLineWithExactDecimalRounding() {
        var p=product("19.99","6");
        var result=QuoteCalculator.calculate(List.of(new QuoteRequests.Line(p.getId(),decimal("3"),decimal("10"))),List.of(p));
        assertThat(result.subtotal()).isEqualByComparingTo("53.97"); assertThat(result.tax()).isEqualByComparingTo("3.24"); assertThat(result.total()).isEqualByComparingTo("57.21");
        assertThat(result.lines().get(0).getUnitPrice()).isEqualByComparingTo("19.99");
        var a=product("0.05","10"); var b=product("0.05","10");
        var rounded=QuoteCalculator.calculate(List.of(new QuoteRequests.Line(a.getId(),BigDecimal.ONE,BigDecimal.ZERO),new QuoteRequests.Line(b.getId(),BigDecimal.ONE,BigDecimal.ZERO)),List.of(a,b));
        assertThat(rounded.tax()).isEqualByComparingTo("0.02"); assertThat(rounded.total()).isEqualByComparingTo("0.12");
    }
    @Test void refusesDuplicateMissingInactiveZeroAndExcessiveLines() {
        var p=product("100","6"); var line=new QuoteRequests.Line(p.getId(),BigDecimal.ONE,BigDecimal.ZERO);
        assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(line,line),List.of(p))).hasMessageContaining("重复");
        assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(line),List.of())).hasMessageContaining("当前团队");
        p.setActive(false); assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(line),List.of(p))).isInstanceOf(CommercialException.class); p.setActive(true);
        for(var quantity:List.of("0","-1","0.0001","1000001")) assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(new QuoteRequests.Line(p.getId(),decimal(quantity),BigDecimal.ZERO)),List.of(p))).isInstanceOf(CommercialException.class);
        for(var discount:List.of("-1","100","1.234")) assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(new QuoteRequests.Line(p.getId(),BigDecimal.ONE,decimal(discount))),List.of(p))).isInstanceOf(CommercialException.class);
        assertThatThrownBy(() -> QuoteCalculator.calculate(Collections.nCopies(51,line),List.of(p))).hasMessageContaining("50");
        p.setListPrice(decimal("999999999.99")); assertThatThrownBy(() -> QuoteCalculator.calculate(List.of(new QuoteRequests.Line(p.getId(),decimal("1000000"),BigDecimal.ZERO)),List.of(p))).hasMessageContaining("总额");
    }
    @Test void paymentPlansMustMatchExactlyAndRemainPositive() {
        ContractService.validatePlans(List.of(new ContractRequests.Installment("A",decimal("0.10"),TODAY),new ContractRequests.Installment("B",decimal("0.20"),TODAY)),decimal("0.30"));
        assertThatThrownBy(() -> ContractService.validatePlans(List.of(new ContractRequests.Installment("A",decimal("0.29"),TODAY)),decimal("0.30"))).hasMessageContaining("合计");
        assertThatThrownBy(() -> ContractService.validatePlans(List.of(),decimal("0.30"))).isInstanceOf(CommercialException.class);
        assertThatThrownBy(() -> ContractService.validatePlans(List.of(new ContractRequests.Installment("A",decimal("-1"),TODAY)),decimal("-1"))).isInstanceOf(CommercialException.class);
        assertThatThrownBy(() -> ContractService.validatePlans(List.of(new ContractRequests.Installment("A",decimal("1.001"),TODAY)),decimal("1.001"))).isInstanceOf(CommercialException.class);
    }
    @Test void readOnlyRolesCannotWriteAndSalesCannotFinalizeOrReceive() {
        for(var role:List.of("GUEST","VIEWER")) assertThatThrownBy(() -> CommercialAccess.writer(actor(role))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> CommercialAccess.manager(actor("SALES"))).isInstanceOf(AccessDeniedException.class);
        assertThat(CommercialAccess.owner(actor("SALES"))).isEqualTo(MEMBER); assertThat(CommercialAccess.owner(actor("OWNER"))).isNull();
        assertThatThrownBy(() -> CommercialAccess.version(2L,1L)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> CommercialAccess.version(0L,null)).isInstanceOf(OptimisticLockingFailureException.class);
        var mapper=mock(CommercialMapper.class); var access=new CommercialAccess(mapper,CLOCK);
        assertThatThrownBy(() -> access.quote(actor("SALES"),UUID.randomUUID())).hasMessageContaining("无权访问");
        verify(mapper).quote(eq(ORG),any(),eq(MEMBER));
    }
    @Test void pendingOrUncertainMailBlocksRevisionAndDecision() {
        var row=new QuoteRow(); row.setApprovalStatus("PENDING"); assertThatThrownBy(() -> QuoteService.requireNoPendingDelivery(row)).hasMessageContaining("邮件");
        row.setApprovalStatus("APPROVED"); row.setActionStatus("EXECUTING"); assertThatThrownBy(() -> QuoteService.requireNoPendingDelivery(row)).isInstanceOf(CommercialException.class);
        row.setActionStatus("FAILED"); row.setDeliveryStatus("UNKNOWN"); assertThatThrownBy(() -> QuoteService.requireNoPendingDelivery(row)).isInstanceOf(CommercialException.class);
        row.setDeliveryStatus("SENT"); assertThatCode(() -> QuoteService.requireNoPendingDelivery(row)).doesNotThrowAnyException();
    }
    @Test void signedFileMustBelongToTheContractCustomer() {
        var mapper=mock(CommercialMapper.class); var files=mock(FileStorageService.class); var access=new CommercialAccess(mapper,CLOCK);
        var id=UUID.randomUUID(); var fileId=UUID.randomUUID(); var contract=new ContractRow(); contract.setId(id); contract.setStatus("DRAFT"); contract.setCustomerId(CUSTOMER); contract.setEndsOn(TODAY.plusYears(1));
        when(mapper.contract(ORG,id,null)).thenReturn(contract); when(mapper.lockContract(ORG,id)).thenReturn(0L);
        when(files.requireActive(ORG,fileId)).thenReturn(UploadedFile.create(fileId,ORG,MEMBER,UUID.randomUUID(),"signed.pdf","application/pdf",new byte[]{1},"hash",CLOCK.instant()));
        var service=new ContractService(mapper,access,CLOCK,files,mock(OpportunityService.class));
        assertThatThrownBy(() -> service.sign(actor("OWNER"),id,new ContractRequests.Sign(0L,fileId,TODAY))).hasMessageContaining("当前客户");
        verify(mapper,never()).signContract(any(),any(),any(),any());
    }
    @Test void csvEscapesSpreadsheetFormulasQuotesAndLeadingWhitespace() {
        assertThat(QuoteDocuments.cell(" =1+1")).isEqualTo("\"' =1+1\"");
        assertThat(QuoteDocuments.cell("@cmd")).isEqualTo("\"'@cmd\"");
        assertThat(QuoteDocuments.cell("A,\"B\"\nC")).isEqualTo("\"A,\"\"B\"\"\nC\"");
        assertThat(QuoteDocuments.cell(null)).isEqualTo("\"\"");
    }
}
