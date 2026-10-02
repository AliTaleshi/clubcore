package ir.clubcore.billing;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.billing.gateway.GatewayRegistry;
import ir.clubcore.billing.gateway.GatewayType;
import ir.clubcore.common.BusinessException;
import ir.clubcore.common.PageResponse;
import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberService;
import ir.clubcore.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
public class BillingController {

    public record CreateInvoice(@NotNull Long memberId, @NotBlank(message = "عنوان الزامی است") @Size(max = 200) String title,
            @Min(value = 1, message = "مبلغ باید مثبت باشد") long amount, @Min(0) long discount) {
    }

    public record PayOffline(@NotNull PaymentMethod method) {
    }

    public record OnlineRequest(@NotNull Long invoiceId) {
    }

    public record ActivateGateway(@NotNull GatewayType gateway) {
    }

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final PaymentRepository paymentRepo;
    private final GatewayRegistry gateways;
    private final MemberService members;
    private final CurrentUser currentUser;

    public BillingController(InvoiceService invoices, PaymentService payments, PaymentRepository paymentRepo,
            GatewayRegistry gateways, MemberService members, CurrentUser currentUser) {
        this.invoices = invoices;
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.gateways = gateways;
        this.members = members;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    public PageResponse<InvoiceDto> list(@RequestParam(required = false) Long memberId,
            @RequestParam(required = false) InvoiceStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invoices.search(memberId, status, page, size);
    }

    @GetMapping("/api/me/invoices")
    @PreAuthorize("hasRole('MEMBER')")
    public PageResponse<InvoiceDto> mine(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invoices.search(members.current().getId(), null, page, size);
    }

    @GetMapping("/api/invoices/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@PathVariable Long id) {
        Invoice i = accessibleInvoice(id);
        List<PaymentDto> ps = paymentRepo.findByInvoiceIdOrderByIdDesc(id).stream().map(PaymentDto::of).toList();
        return Map.of("invoice", InvoiceDto.of(i), "payments", ps);
    }

    @PostMapping("/api/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    @Transactional
    public InvoiceDto create(@Valid @RequestBody CreateInvoice req) {
        Member m = members.get(req.memberId());
        return InvoiceDto.of(invoices.create(m, req.title().trim(), InvoiceKind.OTHER, req.amount(), req.discount()));
    }

    @PostMapping("/api/invoices/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    @Transactional
    public PaymentDto payOffline(@PathVariable Long id, @Valid @RequestBody PayOffline req) {
        return PaymentDto.of(invoices.payOffline(id, req.method(), currentUser.id()));
    }

    @PostMapping("/api/invoices/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    public InvoiceDto cancel(@PathVariable Long id) {
        return invoices.cancel(id);
    }

    @PostMapping("/api/payments/online")
    @Transactional
    public PaymentService.OnlineStart startOnline(@Valid @RequestBody OnlineRequest req) {
        return payments.startOnline(accessibleInvoice(req.invoiceId()));
    }

    @GetMapping("/api/payments/callback/{gateway}")
    public ResponseEntity<Void> callback(@PathVariable String gateway, @RequestParam Map<String, String> params) {
        GatewayType type;
        try {
            type = GatewayType.valueOf(gateway.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.notFound("درگاه");
        }
        String target = payments.handleCallback(type, params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @GetMapping("/api/payments/{id}")
    @Transactional(readOnly = true)
    public PaymentDto payment(@PathVariable Long id) {
        Payment p = payments.get(id);
        accessibleInvoice(p.getInvoice().getId());
        return PaymentDto.of(p);
    }

    @GetMapping("/api/payments/gateways")
    public List<GatewayRegistry.GatewayInfo> gateways() {
        return gateways.list();
    }

    @PutMapping("/api/payments/gateways/active")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GatewayRegistry.GatewayInfo> activate(@Valid @RequestBody ActivateGateway req) {
        gateways.activate(req.gateway());
        return gateways.list();
    }

    private Invoice accessibleInvoice(Long id) {
        Invoice i = invoices.get(id);
        if (currentUser.is(Role.MEMBER) && !i.getMember().getUser().getId().equals(currentUser.id())) {
            throw BusinessException.forbidden();
        }
        if (currentUser.is(Role.COACH)) {
            throw BusinessException.forbidden();
        }
        return i;
    }
}
