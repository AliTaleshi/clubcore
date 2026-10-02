package ir.clubcore.member;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
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

import ir.clubcore.common.PageResponse;
import ir.clubcore.loyalty.LoyaltyService;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipDto;
import ir.clubcore.membership.MembershipService;
import ir.clubcore.notification.NotificationService;
import jakarta.validation.Valid;

@RestController
public class MemberController {

    private final MemberService members;
    private final MembershipService memberships;
    private final LoyaltyService loyalty;
    private final QrTokenService qr;
    private final NotificationService notifications;
    private final ir.clubcore.attendance.AttendanceRepository attendance;
    private final java.time.Clock clock;

    public MemberController(MemberService members, MembershipService memberships, LoyaltyService loyalty,
            QrTokenService qr, NotificationService notifications,
            ir.clubcore.attendance.AttendanceRepository attendance, java.time.Clock clock) {
        this.members = members;
        this.memberships = memberships;
        this.loyalty = loyalty;
        this.qr = qr;
        this.notifications = notifications;
        this.attendance = attendance;
        this.clock = clock;
    }

    @GetMapping("/api/members")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT','COACH')")
    @Transactional(readOnly = true)
    public PageResponse<MemberDto> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return members.search(q, page, size);
    }

    @PostMapping("/api/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @Transactional
    public MemberDto create(@Valid @RequestBody MemberRequest req) {
        return MemberDto.of(members.create(req));
    }

    @GetMapping("/api/members/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@PathVariable Long id) {
        return summary(members.getAccessible(id));
    }

    @PutMapping("/api/members/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public MemberDto update(@PathVariable Long id, @Valid @RequestBody MemberRequest req) {
        return members.update(id, req);
    }

    // ------------------------------------------------------------ self-service

    @GetMapping("/api/me/member")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        Member m = members.current();
        Map<String, Object> out = summary(m);
        out.put("unreadNotifications", notifications.unread(m.getUser().getId()).get("unread"));
        return out;
    }

    @PutMapping("/api/me/member")
    @PreAuthorize("hasRole('MEMBER')")
    public MemberDto updateMe(@RequestBody MemberRequest req) {
        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new ir.clubcore.common.BusinessException("نام الزامی است");
        }
        return members.updateOwnProfile(members.current().getUser().getId(), req);
    }

    @GetMapping("/api/me/qr")
    @PreAuthorize("hasRole('MEMBER')")
    public QrTokenService.QrToken myQr() {
        return qr.generate(members.current().getId());
    }

    private Map<String, Object> summary(Member m) {
        Membership current = memberships.usableToday(m.getId());
        var now = clock.instant();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("member", MemberDto.of(m));
        out.put("currentMembership", current == null ? null : MembershipDto.of(current));
        out.put("entryBlockReason", current == null ? memberships.entryBlockReason(m.getId()) : null);
        out.put("loyalty", loyalty.summary(m));
        out.put("visitsLast30", attendance.countByMemberIdAndCheckInAtBetween(m.getId(),
                now.minus(30, java.time.temporal.ChronoUnit.DAYS), now));
        out.put("totalVisits", attendance.countByMemberId(m.getId()));
        var last = attendance.lastVisit(m.getId());
        out.put("lastVisit", last);
        out.put("inside", attendance.findFirstByMemberIdAndCheckOutAtIsNullOrderByIdDesc(m.getId()).isPresent());
        return out;
    }
}
