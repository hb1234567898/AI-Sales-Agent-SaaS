package com.yourcompany.salesagent.commercial.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.commercial.application.*;
import com.yourcompany.salesagent.commercial.infrastructure.*;
import com.yourcompany.salesagent.shared.api.PageResponse;

@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/commercial")
public class CommercialController {
    private final CatalogService catalog; private final QuoteService quotes; private final ContractService contracts;
    private final QuoteEmailService email; private final QuoteDocuments documents;
    public CommercialController(CatalogService catalog,QuoteService quotes,ContractService contracts,QuoteEmailService email,QuoteDocuments documents) {
        this.catalog=catalog; this.quotes=quotes; this.contracts=contracts; this.email=email; this.documents=documents;
    }
    @GetMapping("/products")
    public PageResponse<ProductRow> products(Authentication auth,@RequestParam(defaultValue="") @Size(max=200) String query,@RequestParam(required=false) Boolean active,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(catalog.products(actor(auth),query,active,page,size)); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED)
    public ProductRow product(Authentication auth,@Valid @RequestBody CatalogRequests.Product input) { return catalog.saveProduct(actor(auth),null,input); }
    @PutMapping("/products/{id}")
    public ProductRow product(Authentication auth,@PathVariable UUID id,@Valid @RequestBody CatalogRequests.Product input) { return catalog.saveProduct(actor(auth),id,input); }
    @GetMapping("/price-books") public List<PriceBookRow> books(Authentication auth) { return catalog.books(actor(auth)); }
    @GetMapping("/price-books/{id}") public CommercialResponses.PriceBookDetail book(Authentication auth,@PathVariable UUID id) { return catalog.book(actor(auth),id); }
    @PostMapping("/price-books") @ResponseStatus(HttpStatus.CREATED)
    public CommercialResponses.PriceBookDetail book(Authentication auth,@Valid @RequestBody CatalogRequests.PriceBook input) { return catalog.saveBook(actor(auth),null,input); }
    @PutMapping("/price-books/{id}") public CommercialResponses.PriceBookDetail book(Authentication auth,@PathVariable UUID id,@Valid @RequestBody CatalogRequests.PriceBook input) { return catalog.saveBook(actor(auth),id,input); }
    @GetMapping("/metrics") public CommercialMetricsRow metrics(Authentication auth) { return contracts.metrics(actor(auth)); }
    @GetMapping("/quotes") public PageResponse<QuoteRow> quotes(Authentication auth,@RequestParam(defaultValue="") @Size(max=200) String query,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(quotes.list(actor(auth),query,status,page,size)); }
    @GetMapping("/quotes/{id}") public CommercialResponses.QuoteDetail quote(Authentication auth,@PathVariable UUID id,@RequestParam(required=false) @Min(1) Integer revision) { return quotes.detail(actor(auth),id,revision); }
    @PostMapping("/quotes/preview") public CommercialResponses.QuotePreview preview(Authentication auth,@Valid @RequestBody QuoteRequests.Upsert input) { return quotes.preview(actor(auth),input); }
    @PostMapping("/quotes") @ResponseStatus(HttpStatus.CREATED)
    public CommercialResponses.QuoteDetail quote(Authentication auth,@Valid @RequestBody QuoteRequests.Upsert input) { return quotes.save(actor(auth),null,input); }
    @PostMapping("/quotes/{id}/versions") public CommercialResponses.QuoteDetail revise(Authentication auth,@PathVariable UUID id,@Valid @RequestBody QuoteRequests.Upsert input) { return quotes.save(actor(auth),id,input); }
    @PostMapping("/quotes/{id}/decisions/{action}") public CommercialResponses.QuoteDetail decision(Authentication auth,@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody QuoteRequests.Decision input) { return quotes.decide(actor(auth),id,action,input); }
    @GetMapping("/quotes/{id}/versions/{revision}/export") public ResponseEntity<byte[]> export(Authentication auth,@PathVariable UUID id,@PathVariable @Min(1) int revision) {
        var detail=quotes.detail(actor(auth),id,revision);
        return download(documents.csv(actor(auth),detail),"Quote-"+detail.quote().getNumber()+"-V"+revision+".csv","text/csv;charset=UTF-8");
    }
    @PostMapping("/quotes/{id}/send") public CommercialResponses.SendResult send(Authentication auth,@PathVariable UUID id,@Valid @RequestBody QuoteRequests.Send input) { return email.propose(actor(auth),id,input); }
    @GetMapping("/contracts") public PageResponse<ContractRow> contracts(Authentication auth,@RequestParam(defaultValue="") @Size(max=200) String query,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(contracts.list(actor(auth),query,status,page,size)); }
    @GetMapping("/contracts/{id}") public CommercialResponses.ContractDetail contract(Authentication auth,@PathVariable UUID id) { return contracts.detail(actor(auth),id); }
    @PostMapping("/contracts") @ResponseStatus(HttpStatus.CREATED)
    public CommercialResponses.ContractDetail contract(Authentication auth,@Valid @RequestBody ContractRequests.Upsert input) { return contracts.save(actor(auth),null,input); }
    @PutMapping("/contracts/{id}") public CommercialResponses.ContractDetail contract(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ContractRequests.Upsert input) { return contracts.save(actor(auth),id,input); }
    @PostMapping("/contracts/{id}/sign") public CommercialResponses.ContractDetail sign(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ContractRequests.Sign input) { return contracts.sign(actor(auth),id,input); }
    @PostMapping("/contracts/{id}/cancel") public CommercialResponses.ContractDetail cancel(Authentication auth,@PathVariable UUID id,@Valid @RequestBody QuoteRequests.Decision input) { return contracts.cancel(actor(auth),id,input); }
    @GetMapping("/contracts/{id}/signed-file") public ResponseEntity<byte[]> signedFile(Authentication auth,@PathVariable UUID id) { var file=contracts.signedFile(actor(auth),id); return download(file.getContent(),file.getOriginalFilename(),"application/octet-stream"); }
    @PostMapping("/contracts/{id}/installments/{plan}/receipts") public CommercialResponses.ContractDetail receive(Authentication auth,@PathVariable UUID id,@PathVariable UUID plan,@Valid @RequestBody ContractRequests.Receipt input) { return contracts.receive(actor(auth),id,plan,input); }
    @GetMapping("/receivables") public PageResponse<PaymentPlanRow> receivables(Authentication auth,@RequestParam(defaultValue="false") boolean overdue,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return PageResponse.from(contracts.receivables(actor(auth),overdue,page,size)); }
    private static AuthPrincipal actor(Authentication auth) { return (AuthPrincipal)auth.getPrincipal(); }
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail invalidQuery(jakarta.validation.ConstraintViolationException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"查询参数不合法，请检查页码、分页数量和版本号"); }
    private static ResponseEntity<byte[]> download(byte[] content,String filename,String type) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(content);
    }
}
