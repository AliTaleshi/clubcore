package ir.clubcore.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.attendance.AttendanceRepository;
import ir.clubcore.common.BusinessException;
import ir.clubcore.crm.ActivityType;
import ir.clubcore.crm.CrmService;
import ir.clubcore.dashboard.KpiService;
import ir.clubcore.loyalty.LoyaltyService;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipService;
import ir.clubcore.notification.SmsService;
import ir.clubcore.setting.SettingService;
import ir.clubcore.user.Role;
import ir.clubcore.user.User;
import ir.clubcore.user.UserService;

@Service
public class AiService {

    public record Reply(String content, String source) {
    }

    public record RetentionMessage(Long memberId, String message, String source, ChurnService.MemberRisk risk) {
    }

    public record Insights(String content, String source, KpiService.Snapshot kpis) {
    }

    private static final String LOCAL = "local";

    private final LlmClient llm;
    private final AiMessageRepository history;
    private final UserService users;
    private final MemberRepository members;
    private final MembershipService memberships;
    private final AttendanceRepository attendance;
    private final LoyaltyService loyalty;
    private final ChurnService churn;
    private final KpiService kpis;
    private final SettingService settings;
    private final SmsService sms;
    private final CrmService crm;
    private final Clock clock;

    public AiService(LlmClient llm, AiMessageRepository history, UserService users, MemberRepository members,
            MembershipService memberships, AttendanceRepository attendance, LoyaltyService loyalty,
            ChurnService churn, KpiService kpis, SettingService settings, SmsService sms, CrmService crm,
            Clock clock) {
        this.llm = llm;
        this.history = history;
        this.users = users;
        this.members = members;
        this.memberships = memberships;
        this.attendance = attendance;
        this.loyalty = loyalty;
        this.churn = churn;
        this.kpis = kpis;
        this.settings = settings;
        this.sms = sms;
        this.crm = crm;
        this.clock = clock;
    }

    public Map<String, Object> status() {
        return Map.of("enabled", llm.enabled(), "model", llm.enabled() ? llm.model() : "local");
    }

    // ---------------------------------------------------------------- chat assistant

    @Transactional
    public Reply chat(Long userId, String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException("پیام خالی است");
        }
        if (message.length() > 2000) {
            throw new BusinessException("پیام بیش از حد طولانی است");
        }
        User user = users.get(userId);
        String context = contextFor(user);
        List<LlmClient.Turn> turns = new ArrayList<>();
        List<AiMessage> past = new ArrayList<>(history.findTop20ByUserIdOrderByIdDesc(userId));
        Collections.reverse(past);
        for (AiMessage m : past) {
            turns.add(new LlmClient.Turn("user".equals(m.getRole()) ? LlmClient.Speaker.USER
                    : LlmClient.Speaker.ASSISTANT, m.getContent()));
        }
        // The API requires alternating turns starting with the user.
        while (!turns.isEmpty() && turns.get(0).speaker() != LlmClient.Speaker.USER) {
            turns.remove(0);
        }
        turns.add(new LlmClient.Turn(LlmClient.Speaker.USER, message.trim()));

        String system = systemPrompt(user) + "\n\n<context>\n" + context + "\n</context>";
        Reply reply = llm.complete(system, normalize(turns), LlmClient.Effort.LOW, 4000)
                .map(t -> new Reply(t, "claude"))
                .orElseGet(() -> new Reply(localChat(user, message, context), LOCAL));
        save(userId, "user", message.trim());
        save(userId, "assistant", reply.content());
        return reply;
    }

    public List<AiMessage> history(Long userId) {
        List<AiMessage> list = new ArrayList<>(history.findTop20ByUserIdOrderByIdDesc(userId));
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public void clearHistory(Long userId) {
        history.deleteByUserId(userId);
    }

    private String systemPrompt(User user) {
        String gym = settings.get("gym.name", "باشگاه");
        String base = "تو دستیار هوشمند «" + gym + "» هستی و همیشه به زبان فارسی روان، دقیق و مختصر پاسخ می‌دهی. "
                + "از Markdown ساده (فهرست و تیتر کوتاه) استفاده کن. اطلاعات داخل تگ context داده‌های واقعی سیستم است؛ "
                + "اعداد را از آن نقل کن و چیزی از خودت نساز.";
        return switch (user.getRole()) {
            case MEMBER -> base + " مخاطب یک عضو باشگاه است. نقش تو مربی و مشاور تمرین و تغذیه عمومی است. "
                    + "برای مسائل پزشکی، آسیب‌دیدگی یا بیماری حتماً توصیه کن با پزشک مشورت کند و تشخیص پزشکی نده.";
            case COACH -> base + " مخاطب یک مربی است. در طراحی برنامه تمرینی، پیگیری شاگردان و انگیزه‌دادن به آن‌ها کمک کن.";
            default -> base + " مخاطب یکی از کارکنان مدیریتی باشگاه است. در تحلیل کسب‌وکار، نگهداشت مشتری، "
                    + "بازاریابی و امور مالی باشگاه با پیشنهادهای عملی کمک کن.";
        };
    }

    private String contextFor(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("تاریخ امروز (میلادی): ").append(LocalDate.now(clock)).append("\n");
        sb.append("نام کاربر: ").append(user.getFullName()).append("\n");
        if (user.getRole() == Role.MEMBER) {
            members.findByUserId(user.getId()).ifPresent(m -> sb.append(memberFacts(m)));
        } else if (user.getRole() == Role.COACH) {
            List<Member> trainees = members.findByCoachId(user.getId());
            sb.append("تعداد شاگردان: ").append(trainees.size()).append("\n");
            for (Member t : trainees.stream().limit(30).toList()) {
                Instant last = attendance.lastVisit(t.getId());
                sb.append("- ").append(t.getFullName()).append(" | هدف: ").append(t.getGoal() == null ? "-" : t.getGoal())
                        .append(" | آخرین مراجعه: ").append(last == null ? "ندارد" : LocalDate.ofInstant(last, clock.getZone()))
                        .append("\n");
            }
        } else {
            KpiService.Snapshot k = kpis.snapshot();
            sb.append(kpiText(k));
        }
        return sb.toString();
    }

    private String memberFacts(Member m) {
        StringBuilder sb = new StringBuilder();
        Instant now = clock.instant();
        sb.append("جنسیت: ").append(m.getGender() == null ? "نامشخص" : m.getGender()).append("\n");
        if (m.getBirthDate() != null) {
            sb.append("سن: ").append(ChronoUnit.YEARS.between(m.getBirthDate(), LocalDate.now(clock))).append("\n");
        }
        sb.append("هدف ورزشی: ").append(m.getGoal() == null ? "ثبت نشده" : m.getGoal()).append("\n");
        Membership ms = memberships.usableToday(m.getId());
        if (ms != null) {
            sb.append("اشتراک فعال: ").append(ms.getPlan().getName()).append(" تا ").append(ms.getEndDate());
            if (ms.sessionsRemaining() != null) {
                sb.append(" | جلسات باقیمانده: ").append(ms.sessionsRemaining());
            }
            sb.append("\n");
        } else {
            sb.append("اشتراک فعال: ندارد\n");
        }
        sb.append("تعداد مراجعات ۳۰ روز اخیر: ")
                .append(attendance.countByMemberIdAndCheckInAtBetween(m.getId(), now.minus(30, ChronoUnit.DAYS), now))
                .append("\n");
        var summary = loyalty.summary(m);
        sb.append("سطح باشگاه مشتریان: ").append(summary.tierTitle()).append(" | امتیاز: ").append(summary.balance())
                .append("\n");
        return sb.toString();
    }

    private static String kpiText(KpiService.Snapshot k) {
        return "اعضای فعال: " + k.activeMembers() + "\n"
                + "حاضر در باشگاه: " + k.presentNow() + "\n"
                + "مراجعات امروز: " + k.todayVisits() + "\n"
                + "مراجعات ۳۰ روز اخیر: " + k.visitsLast30() + " (۳۰ روز قبل از آن: " + k.visitsPrev30() + ")\n"
                + "درآمد ۳۰ روز اخیر (تومان): " + k.revenueLast30() + " (۳۰ روز قبل از آن: " + k.revenuePrev30() + ")\n"
                + "هزینه ۳۰ روز اخیر (تومان): " + k.expenseLast30() + "\n"
                + "اعضای جدید ۳۰ روز اخیر: " + k.newMembersLast30() + "\n"
                + "اشتراک‌های رو به اتمام (۷ روز): " + k.expiringIn7Days() + "\n"
                + "فاکتورهای پرداخت‌نشده: " + k.unpaidInvoices() + "\n"
                + "سرنخ‌های نیازمند پیگیری امروز: " + k.leadsDueToday() + "\n"
                + "اعضای با ریسک ریزش بالا / متوسط: " + k.highChurnRisk() + " / " + k.mediumChurnRisk() + "\n";
    }

    private String localChat(User user, String message, String context) {
        if (user.getRole() == Role.MEMBER) {
            return FallbackTexts.memberChat(message, user.getFullName(), "**وضعیت شما:**\n" + context);
        }
        return "دستیار هوشمند در حال حاضر به سرویس هوش مصنوعی متصل نیست. خلاصه وضعیت فعلی:\n\n" + context;
    }

    /** Merges consecutive same-speaker turns so the conversation strictly alternates. */
    private static List<LlmClient.Turn> normalize(List<LlmClient.Turn> turns) {
        List<LlmClient.Turn> out = new ArrayList<>();
        for (LlmClient.Turn t : turns) {
            if (!out.isEmpty() && out.get(out.size() - 1).speaker() == t.speaker()) {
                LlmClient.Turn prev = out.remove(out.size() - 1);
                out.add(new LlmClient.Turn(t.speaker(), prev.text() + "\n\n" + t.text()));
            } else {
                out.add(t);
            }
        }
        return out;
    }

    private void save(Long userId, String role, String content) {
        AiMessage m = new AiMessage();
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(clock.instant());
        history.save(m);
    }

    // ---------------------------------------------------------------- workout plan

    public Reply workoutPlan(Member member, String goal, String level, int daysPerWeek, String notes) {
        int days = Math.max(2, Math.min(6, daysPerWeek));
        String g = goal != null && !goal.isBlank() ? goal : member != null ? member.getGoal() : null;
        StringBuilder prompt = new StringBuilder("یک برنامه تمرینی هفتگی بنویس.\n");
        prompt.append("هدف: ").append(g == null ? "آمادگی جسمانی عمومی" : g).append("\n");
        prompt.append("سطح: ").append(level == null ? "متوسط" : level).append("\n");
        prompt.append("تعداد جلسات در هفته: ").append(days).append("\n");
        if (member != null) {
            prompt.append("اطلاعات عضو:\n").append(memberFacts(member));
        }
        if (notes != null && !notes.isBlank()) {
            prompt.append("ملاحظات: ").append(notes).append("\n");
        }
        prompt.append("خروجی: برای هر روز، حرکات با تعداد ست و تکرار و زمان استراحت؛ سپس نکات گرم‌کردن، پیشرفت تدریجی "
                + "و یک توصیه تغذیه‌ای کوتاه. فقط Markdown فارسی، بدون مقدمه.");
        String system = "تو یک مربی بدنسازی حرفه‌ای و دارای مدرک هستی که به زبان فارسی برنامه تمرینی ایمن و عملی می‌نویسی. "
                + "اگر ملاحظات پزشکی ذکر شده، برنامه را محافظه‌کارانه طراحی کن و مشورت با پزشک را یادآوری کن.";
        return llm.complete(system, List.of(new LlmClient.Turn(LlmClient.Speaker.USER, prompt.toString())),
                LlmClient.Effort.MEDIUM, 8000)
                .map(t -> new Reply(t, "claude"))
                .orElseGet(() -> new Reply(FallbackTexts.workoutPlan(g, level, days), LOCAL));
    }

    // ---------------------------------------------------------------- retention

    @Transactional(readOnly = true)
    public RetentionMessage retentionMessage(Long memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> BusinessException.notFound("عضو"));
        ChurnService.MemberRisk risk = churn.score(m);
        String gym = settings.get("gym.name", "باشگاه");
        String firstName = m.getFullName().split(" ")[0];
        boolean expired = risk != null && risk.membershipEnd() != null
                && risk.membershipEnd().isBefore(LocalDate.now(clock));
        String prompt = "یک پیامک کوتاه (حداکثر ۱۶۰ نویسه) و صمیمی برای بازگرداندن این عضو به باشگاه «" + gym
                + "» بنویس. نام کوچک: " + firstName + "\n"
                + "دلایل احتمالی ریزش: " + (risk == null ? "نامشخص" : String.join("، ", risk.reasons())) + "\n"
                + "فقط متن پیامک را بنویس، بدون توضیح اضافه، بدون ایموجی و بدون وعده تخفیف مشخص.";
        return llm.complete("تو کارشناس بازاریابی و نگهداشت مشتری یک باشگاه ورزشی هستی و فارسی می‌نویسی.",
                List.of(new LlmClient.Turn(LlmClient.Speaker.USER, prompt)), LlmClient.Effort.LOW, 1000)
                .map(t -> new RetentionMessage(memberId, t.replace("\"", "").trim(), "claude", risk))
                .orElseGet(() -> new RetentionMessage(memberId, FallbackTexts.retentionSms(firstName, gym, expired),
                        LOCAL, risk));
    }

    @Transactional
    public void sendRetentionSms(Long memberId, String message, Long staffId) {
        Member m = members.findById(memberId).orElseThrow(() -> BusinessException.notFound("عضو"));
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new BusinessException("متن پیامک نامعتبر است");
        }
        if (!sms.send(m.getPhone(), message.trim())) {
            throw new BusinessException("ارسال پیامک ناموفق بود");
        }
        crm.log(null, memberId, ActivityType.SMS, "پیامک نگهداشت: " + message.trim(), staffId);
    }

    // ---------------------------------------------------------------- insights

    public Insights insights() {
        KpiService.Snapshot k = kpis.snapshot();
        String prompt = "شاخص‌های کلیدی ۳۰ روز اخیر باشگاه:\n" + kpiText(k)
                + "\nیک تحلیل مدیریتی کوتاه بنویس: ۱) خلاصه وضعیت در ۲-۳ جمله ۲) سه نقطه قوت یا ضعف با استناد به اعداد "
                + "۳) چهار اقدام مشخص و عملی برای هفته آینده (درآمد، نگهداشت اعضا، جذب). Markdown فارسی.";
        return llm.complete("تو مشاور کسب‌وکار باشگاه‌های ورزشی هستی. فقط بر اساس اعداد داده‌شده تحلیل کن.",
                List.of(new LlmClient.Turn(LlmClient.Speaker.USER, prompt)), LlmClient.Effort.MEDIUM, 4000)
                .map(t -> new Insights(t, "claude", k))
                .orElseGet(() -> new Insights(localInsights(k), LOCAL, k));
    }

    static String localInsights(KpiService.Snapshot k) {
        List<String> items = new ArrayList<>();
        if (k.revenuePrev30() > 0) {
            long change = Math.round((k.revenueLast30() - k.revenuePrev30()) * 100.0 / k.revenuePrev30());
            items.add(change >= 0 ? "درآمد ۳۰ روز اخیر " + change + "٪ نسبت به دوره قبل رشد داشته است."
                    : "درآمد ۳۰ روز اخیر " + (-change) + "٪ کاهش یافته؛ کمپین تمدید و فروش پلن‌های بلندمدت را بررسی کنید.");
        }
        if (k.visitsPrev30() > 0 && k.visitsLast30() < k.visitsPrev30() * 0.9) {
            items.add("مراجعات کاهش یافته است؛ برای اعضای کم‌تحرک پیامک انگیزشی ارسال کنید.");
        }
        if (k.expiringIn7Days() > 0) {
            items.add(k.expiringIn7Days() + " اشتراک تا ۷ روز آینده تمام می‌شود؛ پیش از انقضا با آن‌ها تماس بگیرید.");
        }
        if (k.highChurnRisk() > 0) {
            items.add(k.highChurnRisk() + " عضو در ریسک بالای ریزش هستند؛ از بخش «رادار ریزش» پیام نگهداشت بفرستید.");
        }
        if (k.unpaidInvoices() > 0) {
            items.add(k.unpaidInvoices() + " فاکتور پرداخت‌نشده وجود دارد؛ پیگیری وصول انجام شود.");
        }
        if (k.leadsDueToday() > 0) {
            items.add(k.leadsDueToday() + " سرنخ امروز نیاز به پیگیری دارند.");
        }
        if (k.expenseLast30() > k.revenueLast30() && k.revenueLast30() > 0) {
            items.add("هزینه‌های ۳۰ روز اخیر از درآمد بیشتر بوده است؛ هزینه‌های جاری را بازبینی کنید.");
        }
        if (items.isEmpty()) {
            items.add("شاخص‌ها در وضعیت پایدار هستند. برای رشد، برنامه معرفی دوستان (Referral) را تبلیغ کنید.");
        }
        StringBuilder sb = new StringBuilder("## تحلیل خودکار وضعیت باشگاه\n\n");
        items.forEach(i -> sb.append("- ").append(i).append("\n"));
        sb.append("\n_این تحلیل بر اساس قواعد داخلی تولید شده است (سرویس هوش مصنوعی در دسترس نیست)._");
        return sb.toString();
    }
}
