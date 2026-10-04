package ir.clubcore.crm;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Paging;
import ir.clubcore.common.Digits;
import ir.clubcore.common.PageResponse;
import ir.clubcore.common.Phones;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberDto;
import ir.clubcore.member.MemberRequest;
import ir.clubcore.member.MemberService;
import ir.clubcore.notification.SmsService;
import ir.clubcore.setting.SettingService;
import ir.clubcore.user.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class CrmService {

    public record LeadRequest(@NotBlank(message = "نام الزامی است") @Size(max = 120, message = "نام بیش از حد طولانی است") String fullName,
            @NotBlank(message = "شماره موبایل الزامی است") @Size(max = 20) String phone,
            @Size(max = 30, message = "منبع بیش از حد طولانی است") String source, LeadStatus status,
            @Size(max = 200, message = "علاقه‌مندی بیش از حد طولانی است") String interest, Long assignedTo,
            LocalDate followUpDate, @Size(max = 2000, message = "یادداشت بیش از حد طولانی است") String notes) {
    }

    private final LeadRepository leads;
    private final CrmActivityRepository activities;
    private final CampaignRepository campaigns;
    private final SegmentService segments;
    private final MemberService members;
    private final SmsService sms;
    private final SettingService settings;
    private final UserService users;
    private final Clock clock;

    public CrmService(LeadRepository leads, CrmActivityRepository activities, CampaignRepository campaigns,
            SegmentService segments, MemberService members, SmsService sms, SettingService settings, UserService users,
            Clock clock) {
        this.leads = leads;
        this.activities = activities;
        this.campaigns = campaigns;
        this.segments = segments;
        this.members = members;
        this.sms = sms;
        this.settings = settings;
        this.users = users;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- leads

    public PageResponse<Lead> search(LeadStatus status, String q, int page, int size) {
        String query = q == null || q.isBlank() ? null : Digits.toLatin(q.trim());
        return PageResponse.of(leads.search(status, query,
                Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "id"))), l -> l);
    }

    @Transactional
    public Lead create(LeadRequest req, Long userId) {
        Lead l = new Lead();
        apply(l, req);
        leads.save(l);
        log(l.getId(), null, ActivityType.NOTE, "سرنخ ثبت شد" + (req.source() == null ? "" : " (منبع: " + req.source() + ")"),
                userId);
        return l;
    }

    @Transactional
    public Lead update(Long id, LeadRequest req, Long userId) {
        Lead l = get(id);
        if (l.getStatus() == LeadStatus.CONVERTED && req.status() != LeadStatus.CONVERTED) {
            throw new BusinessException("وضعیت سرنخ تبدیل‌شده قابل تغییر نیست");
        }
        if (req.status() == LeadStatus.CONVERTED && l.getStatus() != LeadStatus.CONVERTED) {
            throw new BusinessException("برای تبدیل سرنخ به عضو از گزینه «تبدیل به عضو» استفاده کنید");
        }
        LeadStatus before = l.getStatus();
        apply(l, req);
        if (before != l.getStatus()) {
            log(id, null, ActivityType.NOTE, "تغییر وضعیت به " + l.getStatus(), userId);
        }
        return l;
    }

    @Transactional
    public MemberDto convert(Long id, Long userId) {
        Lead l = get(id);
        if (l.getStatus() == LeadStatus.CONVERTED) {
            throw BusinessException.conflict("این سرنخ قبلاً به عضو تبدیل شده است");
        }
        Member m = members.create(new MemberRequest(l.getFullName(), l.getPhone(), null, null, null, null, null, null,
                null, null, null, l.getInterest(), l.getNotes(), null));
        l.setStatus(LeadStatus.CONVERTED);
        l.setConvertedMemberId(m.getId());
        log(id, m.getId(), ActivityType.NOTE, "سرنخ به عضو شماره " + m.getMembershipNo() + " تبدیل شد", userId);
        return MemberDto.of(m);
    }

    public List<Lead> dueToday() {
        return leads.dueOn(LocalDate.now(clock));
    }

    public Lead get(Long id) {
        return leads.findById(id).orElseThrow(() -> BusinessException.notFound("سرنخ"));
    }

    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (LeadStatus s : LeadStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        leads.countByStatus().forEach(r -> byStatus.put(((LeadStatus) r[0]).name(), ((Number) r[1]).longValue()));
        List<Map<String, Object>> sources = leads.sourceStats().stream().map(r -> Map.<String, Object>of(
                "source", r[0] == null ? "نامشخص" : r[0], "total", ((Number) r[1]).longValue(),
                "converted", ((Number) r[2]).longValue())).toList();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long converted = byStatus.get("CONVERTED");
        return Map.of("byStatus", byStatus, "sources", sources, "total", total, "conversionRate",
                total == 0 ? 0 : Math.round(converted * 1000.0 / total) / 10.0);
    }

    // ---------------------------------------------------------------- activities

    @Transactional
    public CrmActivity log(Long leadId, Long memberId, ActivityType type, String content, Long userId) {
        if (leadId == null && memberId == null) {
            throw new BusinessException("فعالیت باید به یک سرنخ یا عضو مرتبط باشد");
        }
        CrmActivity a = new CrmActivity();
        a.setLeadId(leadId);
        a.setMemberId(memberId);
        a.setType(type);
        a.setContent(content);
        a.setCreatedBy(userId);
        a.setCreatedAt(clock.instant());
        return activities.save(a);
    }

    public List<CrmActivity> leadActivities(Long leadId) {
        return activities.findByLeadIdOrderByIdDesc(leadId);
    }

    public List<CrmActivity> memberActivities(Long memberId) {
        return activities.findByMemberIdOrderByIdDesc(memberId);
    }

    // ---------------------------------------------------------------- segments & campaigns

    public List<MemberDto> segmentMembers(Segment segment) {
        return segments.resolve(segment).stream().map(MemberDto::of).toList();
    }

    /**
     * Sends an SMS to every member in the segment; {name} and {gym} placeholders are substituted. Not transactional:
     * each SMS and its activity log are committed on their own, so a long campaign never holds a connection.
     */
    public Campaign sendCampaign(String title, Segment segment, String message, Long userId) {
        List<Member> audience = segments.resolve(segment);
        String gym = settings.get("gym.name", "باشگاه");
        int sent = 0;
        for (Member m : audience) {
            String text = message.replace("{name}", m.getFullName()).replace("{gym}", gym);
            if (sms.send(m.getPhone(), text)) {
                sent++;
                log(null, m.getId(), ActivityType.SMS, "کمپین «" + title + "»: " + text, userId);
            }
        }
        Campaign c = new Campaign();
        c.setTitle(title);
        c.setSegment(segment);
        c.setMessage(message);
        c.setSentCount(sent);
        c.setCreatedBy(userId);
        return campaigns.save(c);
    }

    public List<Campaign> campaigns() {
        return campaigns.findAllByOrderByIdDesc();
    }

    private void apply(Lead l, LeadRequest req) {
        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new BusinessException("نام الزامی است");
        }
        String phone = Phones.normalize(req.phone());
        if (!Phones.isValid(phone)) {
            throw new BusinessException("شماره موبایل نامعتبر است");
        }
        l.setFullName(req.fullName().trim());
        l.setPhone(phone);
        l.setSource(req.source());
        if (req.status() != null) {
            l.setStatus(req.status());
        }
        l.setInterest(req.interest());
        if (req.assignedTo() != null && !users.get(req.assignedTo()).getRole().isStaff()) {
            throw new BusinessException("مسئول پیگیری باید از کارکنان باشد");
        }
        l.setAssignedTo(req.assignedTo());
        l.setFollowUpDate(req.followUpDate());
        l.setNotes(req.notes());
    }
}
