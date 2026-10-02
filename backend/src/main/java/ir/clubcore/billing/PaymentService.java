package ir.clubcore.billing;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.billing.gateway.GatewayRegistry;
import ir.clubcore.billing.gateway.GatewayType;
import ir.clubcore.billing.gateway.PaymentGateway;
import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record OnlineStart(Long paymentId, String redirectUrl, GatewayType gateway) {
    }

    private final InvoiceService invoices;
    private final PaymentRepository payments;
    private final GatewayRegistry gateways;
    private final AppProperties props;

    public PaymentService(InvoiceService invoices, PaymentRepository payments, GatewayRegistry gateways,
            AppProperties props) {
        this.invoices = invoices;
        this.payments = payments;
        this.gateways = gateways;
        this.props = props;
    }

    @Transactional
    public OnlineStart startOnline(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new BusinessException("این فاکتور قابل پرداخت نیست");
        }
        if (invoice.getTotal() < 1000) {
            throw new BusinessException("حداقل مبلغ پرداخت اینترنتی ۱٬۰۰۰ تومان است");
        }
        PaymentGateway gateway = gateways.active();
        String callback = props.publicUrl() + "/api/payments/callback/" + gateway.type().name().toLowerCase();
        PaymentGateway.StartResult start = gateway.request(invoice.getTotal(),
                "پرداخت فاکتور " + invoice.getNumber() + " - " + invoice.getTitle(), invoice.getMember().getPhone(),
                callback);
        Payment p = new Payment();
        p.setInvoice(invoice);
        p.setAmount(invoice.getTotal());
        p.setMethod(PaymentMethod.ONLINE);
        p.setGateway(gateway.type());
        p.setAuthority(start.authority());
        payments.save(p);
        return new OnlineStart(p.getId(), start.redirectUrl(), gateway.type());
    }

    /**
     * Handles the browser redirect from a gateway and returns the SPA URL to send the user to. Verification is
     * idempotent: replaying a callback for an already-paid payment just shows the result again.
     */
    @Transactional
    public String handleCallback(GatewayType type, Map<String, String> params) {
        PaymentGateway gateway = gateways.get(type);
        String authority = gateway.authorityFrom(params);
        Payment p = authority == null ? null : payments.findByGatewayAndAuthority(type, authority).orElse(null);
        if (p == null) {
            return resultUrl(null, "FAILED");
        }
        if (p.getStatus() != PaymentStatus.PENDING) {
            return resultUrl(p.getId(), p.getStatus().name());
        }
        if (!gateway.callbackSuccessful(params)) {
            p.setStatus(PaymentStatus.FAILED);
            return resultUrl(p.getId(), "FAILED");
        }
        PaymentGateway.VerifyResult result = gateway.verify(authority, p.getAmount());
        if (!result.success()) {
            log.info("Payment {} verification failed: {}", p.getId(), result.message());
            p.setStatus(PaymentStatus.FAILED);
            return resultUrl(p.getId(), "FAILED");
        }
        p.setRefId(result.refId());
        p.setCardPan(result.cardPan());
        Invoice invoice = p.getInvoice();
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            // Paid twice (e.g. cash at reception meanwhile): keep the record so staff can refund.
            log.warn("Invoice {} already {} when online payment {} verified", invoice.getId(), invoice.getStatus(),
                    p.getId());
            p.setStatus(PaymentStatus.PAID);
            return resultUrl(p.getId(), "PAID");
        }
        invoices.settle(invoice, p);
        return resultUrl(p.getId(), "PAID");
    }

    public Payment get(Long id) {
        return payments.findById(id).orElseThrow(() -> BusinessException.notFound("پرداخت"));
    }

    private String resultUrl(Long paymentId, String status) {
        return props.publicUrl() + "/payment/result?status=" + status + (paymentId == null ? "" : "&paymentId=" + paymentId);
    }
}
