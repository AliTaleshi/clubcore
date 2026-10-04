package ir.clubcore.billing;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Paging;
import ir.clubcore.common.PageResponse;
import ir.clubcore.member.Member;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, ApplicationEventPublisher events,
            Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Invoice create(Member member, String title, InvoiceKind kind, long amount, long discount) {
        if (amount < 0 || discount < 0) {
            throw new BusinessException("مبلغ نامعتبر است");
        }
        Invoice i = new Invoice();
        i.setNumber(String.valueOf(invoices.nextNumber()));
        i.setMember(member);
        i.setTitle(title);
        i.setKind(kind);
        i.setAmount(amount);
        i.setDiscount(Math.min(discount, amount));
        i.setTotal(amount - i.getDiscount());
        return invoices.save(i);
    }

    public Invoice get(Long id) {
        return invoices.findById(id).orElseThrow(() -> BusinessException.notFound("فاکتور"));
    }

    /** Loads the invoice with a row lock; use before any status transition. */
    public Invoice lock(Long id) {
        return invoices.lockById(id).orElseThrow(() -> BusinessException.notFound("فاکتور"));
    }

    public PageResponse<InvoiceDto> search(Long memberId, InvoiceStatus status, int page, int size) {
        return PageResponse.of(invoices.search(memberId, status,
                Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "id"))), InvoiceDto::of);
    }

    /** Records a successful payment and fires {@link InvoicePaidEvent}. Idempotent per invoice. */
    @Transactional
    public Payment settle(Invoice invoice, Payment payment) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw BusinessException.conflict("این فاکتور قبلاً پرداخت شده است");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("فاکتور لغو شده قابل پرداخت نیست");
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(clock.instant());
        payments.save(payment);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(payment.getPaidAt());
        events.publishEvent(new InvoicePaidEvent(invoice, payment));
        return payment;
    }

    @Transactional
    public Payment payOffline(Long invoiceId, PaymentMethod method, Long staffId) {
        if (method != PaymentMethod.CASH && method != PaymentMethod.POS) {
            throw new BusinessException("روش پرداخت حضوری باید نقد یا کارتخوان باشد");
        }
        Invoice invoice = lock(invoiceId);
        Payment p = new Payment();
        p.setInvoice(invoice);
        p.setAmount(invoice.getTotal());
        p.setMethod(invoice.getTotal() == 0 ? PaymentMethod.FREE : method);
        p.setRecordedBy(staffId);
        return settle(invoice, p);
    }

    /** Zero-total invoices (e.g. fully discounted) are settled immediately without a payment step. */
    @Transactional
    public void settleIfFree(Invoice invoice) {
        if (invoice.getTotal() == 0 && invoice.getStatus() == InvoiceStatus.UNPAID) {
            Payment p = new Payment();
            p.setInvoice(invoice);
            p.setAmount(0);
            p.setMethod(PaymentMethod.FREE);
            settle(invoice, p);
        }
    }

    @Transactional
    public InvoiceDto cancel(Long id) {
        Invoice i = lock(id);
        if (i.getStatus() != InvoiceStatus.UNPAID) {
            throw new BusinessException("فقط فاکتورهای پرداخت‌نشده قابل لغو هستند");
        }
        i.setStatus(InvoiceStatus.CANCELLED);
        events.publishEvent(new InvoiceCancelledEvent(i));
        return InvoiceDto.of(i);
    }
}
