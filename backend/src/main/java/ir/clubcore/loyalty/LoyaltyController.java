package ir.clubcore.loyalty;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.BusinessException;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.member.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    public record RewardRequest(@NotBlank(message = "عنوان جایزه الزامی است") @Size(max = 120) String title,
            @Size(max = 300) String description, @Min(value = 1, message = "امتیاز لازم باید مثبت باشد") int pointsCost,
            @NotNull RewardType type, @Min(0) long value, Boolean active) {
    }

    public record RedeemRequest(@NotNull Long rewardId, Long memberId) {
    }

    public record AdjustRequest(@NotNull Long memberId, int points, @NotBlank(message = "توضیح الزامی است") String note) {
    }

    public record LeaderRow(Long memberId, String fullName, long points) {
    }

    private final LoyaltyService loyalty;
    private final RewardRepository rewards;
    private final MemberService members;
    private final MemberRepository memberRepo;

    public LoyaltyController(LoyaltyService loyalty, RewardRepository rewards, MemberService members,
            MemberRepository memberRepo) {
        this.loyalty = loyalty;
        this.rewards = rewards;
        this.members = members;
        this.memberRepo = memberRepo;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        Member m = members.current();
        return Map.of("summary", loyalty.summary(m), "history", loyalty.history(m.getId()), "redemptions",
                loyalty.redemptionsOf(m.getId()));
    }

    @GetMapping("/members/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @Transactional(readOnly = true)
    public Map<String, Object> ofMember(@PathVariable Long memberId) {
        Member m = members.get(memberId);
        return Map.of("summary", loyalty.summary(m), "history", loyalty.history(m.getId()), "redemptions",
                loyalty.redemptionsOf(m.getId()));
    }

    @GetMapping("/rewards")
    public List<Reward> rewards(@RequestParam(defaultValue = "false") boolean all) {
        return all ? loyalty.allRewards() : loyalty.activeRewards();
    }

    @PostMapping("/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Reward createReward(@Valid @RequestBody RewardRequest req) {
        Reward r = new Reward();
        apply(r, req);
        return rewards.save(r);
    }

    @PutMapping("/rewards/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Reward updateReward(@PathVariable Long id, @Valid @RequestBody RewardRequest req) {
        Reward r = rewards.findById(id).orElseThrow(() -> BusinessException.notFound("جایزه"));
        apply(r, req);
        return r;
    }

    /** Members redeem for themselves; reception can redeem on behalf of a member. */
    @PostMapping("/redeem")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN','RECEPTIONIST')")
    @Transactional
    public Redemption redeem(@Valid @RequestBody RedeemRequest req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        Member m;
        if ("MEMBER".equals(jwt.getClaimAsString("role"))) {
            m = members.current();
        } else {
            if (req.memberId() == null) {
                throw new BusinessException("عضو را مشخص کنید");
            }
            m = members.get(req.memberId());
        }
        return loyalty.redeem(m, req.rewardId());
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LoyaltyTransaction adjust(@Valid @RequestBody AdjustRequest req) {
        Member m = members.get(req.memberId());
        if (req.points() == 0) {
            throw new BusinessException("مقدار امتیاز نمی‌تواند صفر باشد");
        }
        if (req.points() < 0 && loyalty.summary(m).balance() + req.points() < 0) {
            throw new BusinessException("موجودی امتیاز عضو کافی نیست");
        }
        return loyalty.add(m.getId(), req.points(), LoyaltyReason.ADJUST, req.note().trim());
    }

    @PostMapping("/gifts/{code}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public Redemption deliverGift(@PathVariable String code) {
        return loyalty.markGiftDelivered(code);
    }

    @GetMapping("/leaderboard")
    @Transactional(readOnly = true)
    public List<LeaderRow> leaderboard(@RequestParam(defaultValue = "30") int days) {
        return loyalty.leaderboard(Math.max(1, Math.min(days, 365))).stream().map(r -> {
            Long id = (Long) r[0];
            String name = memberRepo.findById(id).map(Member::getFullName).orElse("-");
            return new LeaderRow(id, name, ((Number) r[1]).longValue());
        }).toList();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public Map<String, Long> stats() {
        return Map.of("issued", loyalty.totalIssued(), "redeemed", loyalty.totalRedeemed());
    }

    private static void apply(Reward r, RewardRequest req) {
        r.setTitle(req.title().trim());
        r.setDescription(req.description());
        r.setPointsCost(req.pointsCost());
        r.setType(req.type());
        r.setValue(req.value());
        if (req.type() == RewardType.DISCOUNT_PERCENT && (req.value() < 1 || req.value() > 100)) {
            throw new BusinessException("درصد تخفیف باید بین ۱ تا ۱۰۰ باشد");
        }
        if (req.active() != null) {
            r.setActive(req.active());
        }
    }
}
