package com.yourcompany.salesagent.commercial.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.api.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;
import com.yourcompany.salesagent.opportunity.application.OpportunityService;
import com.yourcompany.salesagent.opportunity.infrastructure.OpportunityMapper;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.infrastructure.UploadedFileMapper;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;

@EnabledIfEnvironmentVariable(named="COMMERCIAL_TEST_DB_URL",matches=".+")
class CommercialPostgresTests {
    @Test void persistsTheFullQuoteContractAndReceiptLifecycleWithTenantAndVersionGuards() throws Exception {
        var ds=new UnpooledDataSource("org.postgresql.Driver",System.getenv("COMMERCIAL_TEST_DB_URL"),"opportunity_test","");
        try(var connection=ds.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try(var sql=connection.createStatement()) {
                    var schema="commercial_test_"+UUID.randomUUID().toString().replace("-","");
                    sql.execute("CREATE SCHEMA "+schema); sql.execute("SET search_path TO "+schema);
                    for(var migration:new String[]{"V1__initial_schema.sql","V11__uploaded_files.sql","V15__opportunity_management.sql","V17__proactive_follow_ups.sql","V18__email_reply_loop.sql","V19__quote_to_cash.sql"}) {
                        try(var input=getClass().getResourceAsStream("/db/migration/"+migration)) { sql.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
                    }
                }
                var org=UUID.randomUUID(); var member=UUID.randomUUID(); var user=UUID.randomUUID(); var customer=UUID.randomUUID(); var opportunity=UUID.randomUUID();
                execute(connection,"INSERT INTO organization(id,slug,name) VALUES (?,'commercial-test','Team')",org);
                execute(connection,"INSERT INTO app_user(id,email,display_name,auth_provider,auth_subject) VALUES (?,'commercial@test.local','Sales','LOCAL','test')",user);
                execute(connection,"INSERT INTO organization_member(id,organization_id,user_id,role) VALUES (?,?,?,'OWNER')",member,org,user);
                execute(connection,"INSERT INTO customer(id,organization_id,owner_member_id,name) VALUES (?,?,?,'Buyer')",customer,org,member);
                execute(connection,"INSERT INTO opportunity(id,organization_id,customer_id,owner_member_id,name) VALUES (?,?,?,?,'Platform')",opportunity,org,customer,member);
                var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
                config.getTypeHandlerRegistry().register(UUID.class,JdbcType.OTHER,UuidTypeHandler.class); config.getTypeHandlerRegistry().register(UUID.class,UuidTypeHandler.class);
                var pagination=new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL)); config.addInterceptor(pagination);
                config.addMapper(UploadedFileMapper.class);
                for(var resource:new String[]{"commercial/CommercialMapper","opportunity/OpportunityMapper","agent/AgentWorkflowMapper","approval/ApprovalMapper"}) {
                    try(var xml=getClass().getResourceAsStream("/mapper/"+resource+".xml")) { new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse(); }
                }
                var session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(connection);
                var mapper=session.getMapper(CommercialMapper.class); var opportunityMapper=session.getMapper(OpportunityMapper.class);
                var clock=Clock.systemUTC(); var now=clock.instant(); var today=LocalDate.ofInstant(now,ZoneId.of("Asia/Shanghai"));
                var actor=new AuthPrincipal(null,user,org,member,"commercial@test.local","Sales","Team","OWNER",now.plusSeconds(900));
                var sales=new AuthPrincipal(null,user,org,member,actor.email(),"Sales","Team","SALES",actor.expiresAt());
                var stranger=new AuthPrincipal(null,user,UUID.randomUUID(),member,actor.email(),"Sales","Other","OWNER",actor.expiresAt());
                var access=new CommercialAccess(mapper,clock); var catalog=new CatalogService(mapper,clock); var quotes=new QuoteService(mapper,access,clock);
                var files=new FileStorageService(session.getMapper(UploadedFileMapper.class),clock);
                var contracts=new ContractService(mapper,access,clock,files,new OpportunityService(opportunityMapper,clock));
                var product=catalog.saveProduct(actor,null,new CatalogRequests.Product("CRM","CRM","year",new BigDecimal("100"),new BigDecimal("6"),true,"",null));
                var book=catalog.saveBook(actor,null,new CatalogRequests.PriceBook("Annual",today.minusDays(1),today.plusYears(1),true,List.of(new CatalogRequests.PriceEntry(product.getId(),new BigDecimal("80"))),null));
                assertThat(catalog.products(actor,"crm",true,0,20).getTotal()).isEqualTo(1);
                assertThat(catalog.products(stranger,"",null,0,20).getTotal()).isZero();
                var input=new QuoteRequests.Upsert(opportunity,"Platform quote",book.book().getId(),today.plusDays(30),"Annual term",List.of(new QuoteRequests.Line(product.getId(),new BigDecimal("3"),new BigDecimal("10"))),null);
                var v1=quotes.save(sales,null,input); var quoteId=v1.quote().getId();
                assertThat(v1.snapshot().getTotal()).isEqualByComparingTo("228.96");
                assertThat(mapper.quotes(new Page<>(1,20),org,UUID.randomUUID(),"","").getTotal()).isZero();
                assertThatThrownBy(() -> quotes.detail(stranger,quoteId,null)).hasMessageContaining("无权访问");
                catalog.saveProduct(actor,product.getId(),new CatalogRequests.Product("CRM","CRM changed","year",new BigDecimal("200"),new BigDecimal("6"),true,"",0L));
                catalog.saveBook(actor,book.book().getId(),new CatalogRequests.PriceBook("Annual changed",today.minusDays(1),today.plusYears(1),true,List.of(new CatalogRequests.PriceEntry(product.getId(),new BigDecimal("90"))),0L));
                var revise=new QuoteRequests.Upsert(opportunity,"Revised",book.book().getId(),today.plusDays(30),"Updated",input.lines(),0L);
                var v2=quotes.save(sales,quoteId,revise); assertThat(v2.snapshot().getTotal()).isEqualByComparingTo("257.58");
                assertThat(quotes.detail(actor,quoteId,1).snapshot().getTotal()).isEqualByComparingTo("228.96");
                assertThat(quotes.detail(actor,quoteId,1).lines().get(0).getProductName()).isEqualTo("CRM");
                assertThatThrownBy(() -> quotes.save(sales,quoteId,revise)).isInstanceOf(OptimisticLockingFailureException.class);
                assertThatThrownBy(() -> quotes.decide(sales,quoteId,"issue",new QuoteRequests.Decision(1L,"Reviewed"))).isInstanceOf(AccessDeniedException.class);
                quotes.decide(actor,quoteId,"issue",new QuoteRequests.Decision(1L,"Reviewed"));
                var agent=session.getMapper(AgentWorkflowMapper.class); var approvalMapper=session.getMapper(ApprovalMapper.class);
                var email=new QuoteEmailService(mapper,access,quotes,new QuoteDocuments(),files,agent,mock(EmailConfigurationService.class),clock);
                var proposed=email.propose(sales,quoteId,new QuoteRequests.Send(2L,"buyer@test.local","Quote V2","Please check"));
                var approval=approvalMapper.selectApproval(org,proposed.approvalId()); assertThat(approval.getStatus()).isEqualTo("PENDING");
                assertThat(approval.getPreview()).containsEntry("attachmentRequired",true); assertThat(approval.getPreview().get("attachments")).asList().hasSize(1);
                assertThatThrownBy(() -> quotes.decide(actor,quoteId,"accept",new QuoteRequests.Decision(3L,"Buyer confirmed"))).hasMessageContaining("邮件");
                assertThatThrownBy(() -> email.propose(sales,quoteId,new QuoteRequests.Send(3L,"buyer@test.local","Again","Again"))).hasMessageContaining("已有邮件");
                approvalMapper.updateDecision(org,proposed.approvalId(),member,"REJECTED","Do not send",0L,clock.instant());
                quotes.decide(actor,quoteId,"accept",new QuoteRequests.Decision(3L,"Buyer confirmed on call"));
                assertThat(quotes.list(actor,"Revised","ACCEPTED",0,20).getTotal()).isEqualTo(1);
                var contractInput=new ContractRequests.Upsert(quoteId,"Platform contract",today,today.plusYears(1),"Term",List.of(new ContractRequests.Installment("First",new BigDecimal("100"),today.minusDays(1)),new ContractRequests.Installment("Final",new BigDecimal("157.58"),today.plusDays(30))),null);
                var draft=contracts.save(sales,null,contractInput); var contractId=draft.contract().getId();
                assertThat(draft.contract().getQuoteRevision()).isEqualTo(2); assertThat(contracts.list(actor,quoteId.toString(),"",0,20).getTotal()).isEqualTo(1);
                assertThatThrownBy(() -> contracts.save(sales,null,contractInput)).hasMessageContaining("已经生成合同");
                var first=draft.installments().get(0); var key=UUID.randomUUID(); var receipt=new ContractRequests.Receipt(1L,new BigDecimal("100.00"),today,"BANK-1","First",key);
                assertThatThrownBy(() -> contracts.receive(actor,contractId,first.getId(),new ContractRequests.Receipt(0L,new BigDecimal("10"),today,"BEFORE","",UUID.randomUUID()))).hasMessageContaining("已签约");
                var signedFile=files.uploadContent(actor,customer,"signed.pdf","application/pdf","%PDF-test".getBytes(StandardCharsets.UTF_8));
                contracts.sign(actor,contractId,new ContractRequests.Sign(0L,signedFile.id(),today));
                assertThat(opportunityMapper.selectOne(org,opportunity).getStatus()).isEqualTo("WON");
                assertThat(contracts.signedFile(actor,contractId).getId()).isEqualTo(signedFile.id());
                assertThat(contracts.receivables(actor,true,0,20).getTotal()).isEqualTo(1);
                assertThat(contracts.metrics(actor).getOverdueAmount()).isEqualByComparingTo("100");
                assertThatThrownBy(() -> contracts.receive(sales,contractId,first.getId(),receipt)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> contracts.receive(actor,contractId,first.getId(),new ContractRequests.Receipt(1L,new BigDecimal("100.01"),today,"OVER","",UUID.randomUUID()))).hasMessageContaining("不能超过");
                assertThatThrownBy(() -> contracts.receive(actor,contractId,first.getId(),new ContractRequests.Receipt(1L,new BigDecimal("1"),today.plusDays(1),"FUTURE","",UUID.randomUUID()))).hasMessageContaining("日期");
                var partial=contracts.receive(actor,contractId,first.getId(),receipt); assertThat(partial.contract().getPaid()).isEqualByComparingTo("100");
                assertThat(contracts.receive(actor,contractId,first.getId(),receipt).receipts()).hasSize(1);
                assertThatThrownBy(() -> contracts.receive(actor,contractId,first.getId(),new ContractRequests.Receipt(2L,new BigDecimal("99"),today,"BANK-1","First",key))).hasMessageContaining("不同记录");
                assertThat(contracts.receivables(actor,true,0,20).getTotal()).isZero();
                var last=partial.installments().get(1);
                assertThatThrownBy(() -> contracts.receive(actor,contractId,last.getId(),new ContractRequests.Receipt(2L,new BigDecimal("1"),today,"BANK-1","",UUID.randomUUID()))).hasMessageContaining("重复");
                var settled=contracts.receive(actor,contractId,last.getId(),new ContractRequests.Receipt(2L,new BigDecimal("157.58"),today,"BANK-2","Final",UUID.randomUUID()));
                assertThat(settled.contract().getStatus()).isEqualTo("COMPLETED"); assertThat(settled.receipts()).hasSize(2);
                assertThat(contracts.metrics(actor).getReceivable()).isZero(); assertThat(contracts.receivables(actor,false,0,20).getTotal()).isZero();
                assertThat(contracts.metrics(stranger).getSignedAmount()).isZero(); assertThat(mapper.plans(org,contractId,UUID.randomUUID(),today)).isEmpty();
                assertThatThrownBy(() -> contracts.cancel(actor,contractId,new QuoteRequests.Decision(3L,"No"))).hasMessageContaining("草稿");
                try(var sql=connection.createStatement()) { sql.execute("SET CONSTRAINTS ALL IMMEDIATE"); }
                assertThat(mapper.events(org,"CONTRACT",contractId)).hasSize(4);
            } finally { connection.rollback(); }
        }
    }
    private static void execute(Connection connection,String sql,Object... values) throws SQLException { try(var statement=connection.prepareStatement(sql)) { for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); statement.executeUpdate(); } }
}
