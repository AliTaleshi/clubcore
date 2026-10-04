package ir.clubcore.membership;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.billing.Invoice;
import ir.clubcore.billing.InvoiceCancelledEvent;
import ir.clubcore.billing.InvoiceKind;
import ir.clubcore.billing.InvoicePaidEvent;
import ir.clubcore.billing.InvoiceService;
import ir.clubcore.common.BusinessException;
import ir.clubcore.loyalty.LoyaltyService;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.notification.NotificationService;
import ir.clubcore.plan.Plan;
import ir.clubcore.plan.PlanRepository;

@Service
public class MembershipService {

    public record Quote(long price, int tierDiscountPercent, long tierDiscount, long codeDiscount, long total) {
    }

    public record PurchaseResult(MembershipDto membership, Long invoiceId, long total, boolean paid) {
    }

    private static final EnumSet<MembershipStatus> CURRENT = EnumSet.of(MembershipStatus.ACTIVE,
            MembershipStatus.FROZEN);

    private final MembershipRepository repo;
    private final PlanRepository plans;
    private final InvoiceService invoices;
    private final LoyaltyService loyalty;
    private final NotificationService notifications;
    private final MemberRepository members;
    private final Clock clock;

    public MembershipService(MembershipRepository repo, PlanRepository plans, InvoiceService invoices,
            LoyaltyService loyalty, NotificationService notifications, MemberRepository members, Clock clock) {
        this.repo = repo;
        this.plans = plans;
        this.invoices = invoices;
        this.loyalty = loyalty;
        this.notifications = notifications;
        this.members = members;
        this.clock = clock;
    }

    public Quote quote(Member member, Long planId, String discountCode) {
        Plan plan = activePlan(planId);
        return quote(member, plan, discountCode);
    }

    private Quote quote(Member member, Plan plan, String discountCode) {
        long price = plan.getPrice();
        int pct = loyalty.discountPercent(member.getId());
        long tierDiscount = price * pct / 100;
        long codeDiscount = 0;
        if (discountCode != null && !discountCode.isBlank()) {
            codeDiscount = loyalty.resolveDiscount(discountCode, member, price - tierDiscount).discount();
        }
        long discount = Math.min(price, tierDiscount + codeDiscount);
        return new Quote(price, pct, tierDiscount, codeDiscount, price - discount);
    }

    /**
     * Creates a pending membership and its invoice. The membership becomes active when the invoice is paid; a zero
     * total (fully discounted) is settled immediately.
     */
    @Transactional
    public PurchaseResult purchase(Member member, Long planId, LocalDate requestedStart, String discountCode) {
        members.lockById(member.getId());
        Plan plan = activePlan(planId);
        LocalDate today = LocalDate.now(clock);
        if (requestedStart != null && requestedStart.isBefore(today)) {
            throw new BusinessException("تاریخ شروع نمی‌تواند در گذشته باشد");
        }
        boolean hasPending = repo.countByMemberIdAndStatusIn(member.getId(),
                EnumSet.of(MembershipStatus.PENDING_PAYMENT)) > 0;
        if (hasPending) {
            throw new BusinessException("این عضو یک اشتراک در انتظار پرداخت دارد؛ ابتدا آن را پرداخت یا لغو کنید");
        }
        Quote q = quote(member, plan, discountCode);
        Invoice invoice = invoices.create(member, "اشتراک " + plan.getName(), InvoiceKind.MEMBERSHIP, q.price(),
                q.price() - q.total());
        if (discountCode != null && !discountCode.isBlank()) {
            loyalty.resolveDiscount(discountCode, member, q.price()).redemption().setInvoiceId(invoice.getId());
        }
        Membership ms = new Membership();
        ms.setMember(member);
        ms.setPlan(plan);
        ms.setInvoice(invoice);
        ms.setStartDate(requestedStart != null ? requestedStart : today);
        ms.setEndDate(ms.getStartDate().plusDays(plan.getDurationDays() - 1L));
        ms.setSessionsTotal(plan.getSessionLimit());
        ms.setStatus(MembershipStatus.PENDING_PAYMENT);
        ms.setPrice(q.price());
        ms.setDiscount(q.price() - q.total());
        repo.save(ms);
        invoices.settleIfFree(invoice);
        return new PurchaseResult(MembershipDto.of(ms), invoice.getId(), q.total(),
                ms.getStatus() == MembershipStatus.ACTIVE);
    }

    @EventListener
    @Transactional
    public void onInvoicePaid(InvoicePaidEvent event) {
        repo.findByInvoiceId(event.invoice().getId()).ifPresent(this::activate);
    }

    @EventListener
    @Transactional
    public void onInvoiceCancelled(InvoiceCancelledEvent event) {
        repo.findByInvoiceId(event.invoice().getId())
                .filter(ms -> ms.getStatus() == MembershipStatus.PENDING_PAYMENT)
                .ifPresent(ms -> ms.setStatus(MembershipStatus.CANCELLED));
    }

    /**
     * Activation stacks renewals: if the member still has a current membership, the new one starts the day after it
     * ends. A session-based membership whose sessions are used up no longer counts and is expired right away, so the
     * renewal starts immediately instead of after the old end date.
     */
    void activate(Membership ms) {
        if (ms.getStatus() != MembershipStatus.PENDING_PAYMENT) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = ms.getStartDate().isBefore(today) ? today : ms.getStartDate();
        for (Membership other : repo.findByMemberIdAndStatusIn(ms.getMember().getId(), CURRENT)) {
            if (other.getId().equals(ms.getId())) {
                continue;
            }
            if (other.getStatus() == MembershipStatus.ACTIVE && !other.hasSessionsLeft()) {
                other.setStatus(MembershipStatus.EXPIRED);
                continue;
            }
            if (!other.getEndDate().isBefore(start)) {
                start = other.getEndDate().plusDays(1);
            }
        }
        ms.setStartDate(start);
        ms.setEndDate(start.plusDays(ms.getPlan().getDurationDays() - 1L));
        ms.setStatus(MembershipStatus.ACTIVE);
        notifications.notify(ms.getMember().getUser().getId(), "اشتراک فعال شد",
                "اشتراک «" + ms.getPlan().getName() + "» شما فعال شد.");
    }

    @Transactional
    public MembershipDto freeze(Long id) {
        Membership ms = get(id);
        LocalDate today = LocalDate.now(clock);
        if (ms.getStatus() != MembershipStatus.ACTIVE) {
            throw new BusinessException("فقط اشتراک فعال قابل فریز است");
        }
        if (ms.getPlan().getMaxFreezeDays() - ms.getFreezeDaysUsed() <= 0) {
            throw new BusinessException("سقف روزهای فریز این اشتراک تمام شده است");
        }
        if (ms.getEndDate().isBefore(today)) {
            throw new BusinessException("این اشتراک به پایان رسیده است");
        }
        if (ms.getStartDate().isAfter(today)) {
            throw new BusinessException("اشتراکی که هنوز شروع نشده قابل فریز نیست");
        }
        if (!ms.hasSessionsLeft()) {
            throw new BusinessException("جلسات این اشتراک تمام شده است");
        }
        ms.setStatus(MembershipStatus.FROZEN);
        ms.setFrozenSince(today);
        return MembershipDto.of(ms);
    }

    @Transactional
    public MembershipDto unfreeze(Long id) {
        Membership ms = get(id);
        if (ms.getStatus() != MembershipStatus.FROZEN) {
            throw new BusinessException("این اشتراک فریز نیست");
        }
        doUnfreeze(ms, LocalDate.now(clock));
        return MembershipDto.of(ms);
    }

    /** Extends the end date by the frozen days, capped at the plan's remaining freeze budget. */
    void doUnfreeze(Membership ms, LocalDate today) {
        int remaining = ms.getPlan().getMaxFreezeDays() - ms.getFreezeDaysUsed();
        int days = (int) Math.min(remaining, Math.max(0, ChronoUnit.DAYS.between(ms.getFrozenSince(), today)));
        ms.setEndDate(ms.getEndDate().plusDays(days));
        ms.setFreezeDaysUsed(ms.getFreezeDaysUsed() + days);
        ms.setFrozenSince(null);
        ms.setStatus(MembershipStatus.ACTIVE);
    }

    @Transactional
    public MembershipDto cancel(Long id) {
        Membership ms = get(id);
        if (ms.getStatus() == MembershipStatus.PENDING_PAYMENT) {
            invoices.cancel(ms.getInvoice().getId());
            return MembershipDto.of(ms);
        }
        if (!CURRENT.contains(ms.getStatus())) {
            throw new BusinessException("این اشتراک قابل لغو نیست");
        }
        ms.setStatus(MembershipStatus.CANCELLED);
        return MembershipDto.of(ms);
    }

    /** Nightly maintenance: expire finished memberships, auto-unfreeze exhausted freezes, send reminders. */
    @Transactional
    public int runDailyMaintenance() {
        LocalDate today = LocalDate.now(clock);
        for (Membership ms : repo.frozen()) {
            int remaining = ms.getPlan().getMaxFreezeDays() - ms.getFreezeDaysUsed();
            if (ChronoUnit.DAYS.between(ms.getFrozenSince(), today) >= remaining) {
                doUnfreeze(ms, today);
            }
        }
        List<Membership> expired = repo.toExpire(today);
        expired.forEach(ms -> ms.setStatus(MembershipStatus.EXPIRED));
        for (Membership ms : repo.endingOn(today.plusDays(3))) {
            notifications.notify(ms.getMember().getUser().getId(), "یادآوری تمدید",
                    "اشتراک «" + ms.getPlan().getName() + "» شما ۳ روز دیگر به پایان می‌رسد.");
        }
        return expired.size();
    }

    public Membership get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("اشتراک"));
    }

    public List<MembershipDto> ofMember(Long memberId) {
        return repo.findByMemberIdOrderByIdDesc(memberId).stream().map(MembershipDto::of).toList();
    }

    /** The membership a member would use to enter today, if any. */
    public Membership usableToday(Long memberId) {
        List<Membership> list = repo.usableOn(memberId, LocalDate.now(clock));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Explains in Persian why a member cannot enter. */
    public String entryBlockReason(Long memberId) {
        List<Membership> all = repo.findByMemberIdOrderByIdDesc(memberId);
        LocalDate today = LocalDate.now(clock);
        if (all.isEmpty()) {
            return "این عضو اشتراکی ندارد";
        }
        if (all.stream().anyMatch(m -> m.getStatus() == MembershipStatus.FROZEN)) {
            return "اشتراک این عضو فریز شده است";
        }
        if (all.stream().anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getStartDate().isAfter(today))) {
            return "اشتراک این عضو هنوز شروع نشده است";
        }
        if (all.stream().anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE && !m.hasSessionsLeft())) {
            return "جلسات اشتراک این عضو تمام شده است";
        }
        if (all.stream().anyMatch(m -> m.getStatus() == MembershipStatus.PENDING_PAYMENT)) {
            return "اشتراک این عضو در انتظار پرداخت است";
        }
        return "اشتراک این عضو منقضی شده است";
    }

    private Plan activePlan(Long planId) {
        return plans.findById(planId).filter(Plan::isActive)
                .orElseThrow(() -> BusinessException.notFound("پلن فعال"));
    }
}
