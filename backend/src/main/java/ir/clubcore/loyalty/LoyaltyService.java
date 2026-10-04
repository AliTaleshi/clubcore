package ir.clubcore.loyalty;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.billing.InvoiceCancelledEvent;
import ir.clubcore.billing.InvoicePaidEvent;
import ir.clubcore.billing.InvoiceRepository;
import ir.clubcore.billing.InvoiceStatus;
import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Codes;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.notification.NotificationService;
import ir.clubcore.setting.SettingService;

@Service
public class LoyaltyService {

    public record Summary(long balance, long lifetimePoints, Tier tier, String tierTitle, Tier nextTier,
            long pointsToNextTier, int discountPercent, String referralCode, long referrals) {
    }

    public record TierInfo(Tier tier, int discountPercent, Tier next, long pointsToNext) {
    }

    /** Discount a redemption code gives on a given price. */
    public record AppliedRedemption(Redemption redemption, long discount) {
    }

    private final LoyaltyTransactionRepository txs;
    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final MemberRepository members;
    private final InvoiceRepository invoices;
    private final SettingService settings;
    private final NotificationService notifications;
    private final Clock clock;

    public LoyaltyService(LoyaltyTransactionRepository txs, RewardRepository rewards,
            RedemptionRepository redemptions, MemberRepository members, InvoiceRepository invoices,
            SettingService settings, NotificationService notifications, Clock clock) {
        this.txs = txs;
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.members = members;
        this.invoices = invoices;
        this.settings = settings;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- tiers

    public TierInfo tierFor(long lifetimePoints) {
        long silver = settings.getLong("loyalty.silverThreshold", 1000);
        long gold = settings.getLong("loyalty.goldThreshold", 3000);
        if (lifetimePoints >= gold) {
            return new TierInfo(Tier.GOLD, (int) settings.getLong("loyalty.goldDiscountPercent", 10), null, 0);
        }
        if (lifetimePoints >= silver) {
            return new TierInfo(Tier.SILVER, (int) settings.getLong("loyalty.silverDiscountPercent", 5), Tier.GOLD,
                    gold - lifetimePoints);
        }
        return new TierInfo(Tier.BRONZE, 0, Tier.SILVER, silver - lifetimePoints);
    }

    public int discountPercent(Long memberId) {
        return tierFor(txs.lifetime(memberId)).discountPercent();
    }

    public Summary summary(Member m) {
        long lifetime = txs.lifetime(m.getId());
        TierInfo t = tierFor(lifetime);
        return new Summary(txs.balance(m.getId()), lifetime, t.tier(), t.tier().title(), t.next(), t.pointsToNext(),
                t.discountPercent(), m.getReferralCode(), members.countByReferredById(m.getId()));
    }

    public List<LoyaltyTransaction> history(Long memberId) {
        return txs.findTop50ByMemberIdOrderByIdDesc(memberId);
    }

    // ---------------------------------------------------------------- earning

    @Transactional
    public LoyaltyTransaction add(Long memberId, int points, LoyaltyReason reason, String reference) {
        LoyaltyTransaction t = new LoyaltyTransaction();
        t.setMemberId(memberId);
        t.setPoints(points);
        t.setReason(reason);
        t.setReference(reference);
        t.setCreatedAt(clock.instant());
        return txs.save(t);
    }

    /** Awards check-in points at most once per calendar day. */
    @Transactional
    public void onCheckIn(Member m) {
        String ref = "CHECKIN:" + LocalDate.now(clock);
        if (txs.existsByMemberIdAndReasonAndReference(m.getId(), LoyaltyReason.CHECKIN, ref)) {
            return;
        }
        int points = (int) settings.getLong("loyalty.checkinPoints", 10);
        if (points > 0) {
            add(m.getId(), points, LoyaltyReason.CHECKIN, ref);
        }
    }

    @EventListener
    @Transactional
    public void onInvoicePaid(InvoicePaidEvent event) {
        var invoice = event.invoice();
        Member m = invoice.getMember();
        long perPoint = Math.max(1, settings.getLong("loyalty.tomanPerPoint", 10000));
        int points = (int) (invoice.getTotal() / perPoint);
        if (points > 0) {
            add(m.getId(), points, LoyaltyReason.PURCHASE, "INVOICE:" + invoice.getNumber());
        }
        redemptions.findByInvoiceId(invoice.getId()).ifPresent(r -> {
            r.setStatus(RedemptionStatus.USED);
            r.setUsedAt(clock.instant());
        });
        // Referral bonus on the referred member's first invoice actually paid for (free invoices don't count).
        Member referrer = m.getReferredBy();
        if (referrer != null && invoice.getTotal() > 0
                && invoices.countByMemberIdAndStatusAndTotalGreaterThan(m.getId(), InvoiceStatus.PAID, 0) == 1) {
            String ref = "REFERRAL:" + m.getId();
            if (!txs.existsByMemberIdAndReasonAndReference(referrer.getId(), LoyaltyReason.REFERRAL, ref)) {
                int bonus = (int) settings.getLong("loyalty.referralPoints", 200);
                add(referrer.getId(), bonus, LoyaltyReason.REFERRAL, ref);
                notifications.notify(referrer.getUser().getId(), "امتیاز معرفی دوستان",
                        bonus + " امتیاز بابت عضویت " + m.getFullName() + " به حساب شما اضافه شد.");
            }
        }
    }

    @EventListener
    @Transactional
    public void onInvoiceCancelled(InvoiceCancelledEvent event) {
        // Release a reserved redemption code so it can be used again.
        redemptions.findByInvoiceId(event.invoice().getId()).ifPresent(r -> r.setInvoiceId(null));
    }

    // ---------------------------------------------------------------- rewards

    public List<Reward> activeRewards() {
        return rewards.findByActiveTrueOrderByPointsCostAsc();
    }

    public List<Reward> allRewards() {
        return rewards.findAll();
    }

    @Transactional
    public Redemption redeem(Member m, Long rewardId) {
        members.lockById(m.getId());
        Reward reward = rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> BusinessException.notFound("جایزه"));
        long balance = txs.balance(m.getId());
        if (balance < reward.getPointsCost()) {
            throw new BusinessException("امتیاز شما برای دریافت این جایزه کافی نیست");
        }
        Redemption r = new Redemption();
        r.setMemberId(m.getId());
        r.setReward(reward);
        String code;
        do {
            code = Codes.random(8);
        } while (redemptions.findByCode(code).isPresent());
        r.setCode(code);
        r.setCreatedAt(clock.instant());
        redemptions.save(r);
        add(m.getId(), -reward.getPointsCost(), LoyaltyReason.REDEEM, "REWARD:" + reward.getId() + ":" + code);
        return r;
    }

    /** Manual correction by an admin; the balance may not go negative. */
    @Transactional
    public LoyaltyTransaction adjust(Member m, int points, String note) {
        if (points == 0) {
            throw new BusinessException("مقدار امتیاز نمی‌تواند صفر باشد");
        }
        members.lockById(m.getId());
        if (points < 0 && txs.balance(m.getId()) + points < 0) {
            throw new BusinessException("موجودی امتیاز عضو کافی نیست");
        }
        return add(m.getId(), points, LoyaltyReason.ADJUST, note);
    }

    public List<Redemption> redemptionsOf(Long memberId) {
        return redemptions.findByMemberIdOrderByIdDesc(memberId);
    }

    /** Validates a discount code for a purchase by the given member and computes the discount on {@code price}. */
    public AppliedRedemption resolveDiscount(String code, Member m, long price) {
        Redemption r = redemptions.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("کد تخفیف نامعتبر است"));
        if (!r.getMemberId().equals(m.getId())) {
            throw new BusinessException("این کد تخفیف متعلق به عضو دیگری است");
        }
        if (r.getStatus() == RedemptionStatus.USED || r.getInvoiceId() != null) {
            throw new BusinessException("این کد تخفیف قبلاً استفاده شده است");
        }
        long discount = switch (r.getReward().getType()) {
            case DISCOUNT_PERCENT -> price * r.getReward().getValue() / 100;
            case DISCOUNT_AMOUNT -> Math.min(price, r.getReward().getValue());
            case GIFT -> throw new BusinessException("این کد مربوط به هدیه است و تخفیف ندارد");
        };
        return new AppliedRedemption(r, discount);
    }

    /** Gift rewards are handed over at reception; staff marks the code used. */
    @Transactional
    public Redemption markGiftDelivered(String code) {
        Redemption r = redemptions.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("کد نامعتبر است"));
        if (r.getStatus() == RedemptionStatus.USED) {
            throw new BusinessException("این کد قبلاً استفاده شده است");
        }
        if (r.getReward().getType() != RewardType.GIFT) {
            throw new BusinessException("این کد تخفیف است و باید هنگام خرید اشتراک استفاده شود");
        }
        r.setStatus(RedemptionStatus.USED);
        r.setUsedAt(clock.instant());
        return r;
    }

    public List<Object[]> leaderboard(int days) {
        return txs.leaderboard(LocalDate.now(clock).minusDays(days).atStartOfDay(ZoneId.of("Asia/Tehran")).toInstant());
    }

    public long totalIssued() {
        return txs.totalIssued();
    }

    public long totalRedeemed() {
        return txs.totalRedeemed();
    }
}
