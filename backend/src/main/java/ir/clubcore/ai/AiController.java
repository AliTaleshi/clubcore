package ir.clubcore.ai;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberService;
import ir.clubcore.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    public record ChatRequest(@NotBlank(message = "پیام خالی است") @Size(max = 2000) String message) {
    }

    public record WorkoutRequest(Long memberId, @Size(max = 200) String goal, @Size(max = 20) String level,
            @Min(2) @Max(6) int daysPerWeek, @Size(max = 500) String notes) {
    }

    public record SmsRequest(@NotBlank @Size(max = 500) String message) {
    }

    private final AiService ai;
    private final ChurnService churn;
    private final MemberService members;
    private final CurrentUser currentUser;

    public AiController(AiService ai, ChurnService churn, MemberService members, CurrentUser currentUser) {
        this.ai = ai;
        this.churn = churn;
        this.members = members;
        this.currentUser = currentUser;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return ai.status();
    }

    @PostMapping("/chat")
    public AiService.Reply chat(@Valid @RequestBody ChatRequest req) {
        return ai.chat(currentUser.id(), req.message());
    }

    @GetMapping("/chat/history")
    public List<AiMessage> history() {
        return ai.history(currentUser.id());
    }

    @DeleteMapping("/chat/history")
    public void clear() {
        ai.clearHistory(currentUser.id());
    }

    @GetMapping("/churn")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public List<ChurnService.MemberRisk> churn() {
        return churn.scoreAll();
    }

    @PostMapping("/workout-plan")
    @PreAuthorize("hasAnyRole('ADMIN','COACH','MEMBER')")
    @Transactional(readOnly = true)
    public AiService.Reply workoutPlan(@Valid @RequestBody WorkoutRequest req) {
        Member m;
        if (currentUser.is(Role.MEMBER)) {
            m = members.current();
        } else {
            m = req.memberId() == null ? null : members.getAccessible(req.memberId());
        }
        return ai.workoutPlan(m, req.goal(), req.level(), req.daysPerWeek(), req.notes());
    }

    @PostMapping("/retention-message/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public AiService.RetentionMessage retention(@PathVariable Long memberId) {
        return ai.retentionMessage(memberId);
    }

    @PostMapping("/retention-message/{memberId}/send")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public void sendRetention(@PathVariable Long memberId, @Valid @RequestBody SmsRequest req) {
        ai.sendRetentionSms(memberId, req.message(), currentUser.id());
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public AiService.Insights insights() {
        return ai.insights();
    }
}
