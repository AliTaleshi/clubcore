package ir.clubcore.attendance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Digits;
import ir.clubcore.common.PageResponse;
import ir.clubcore.common.Phones;
import ir.clubcore.loyalty.LoyaltyService;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.member.QrTokenService;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipService;

@Service
public class AttendanceService {

    public enum Action {
        CHECK_IN, CHECK_OUT
    }

    /** Result shown on the kiosk / reception screen. */
    public record ScanResult(Action action, AttendanceDto attendance, String memberName, String membershipNo,
            String planName, java.time.LocalDate endDate, Integer sessionsRemaining, String message) {
    }

    private final AttendanceRepository repo;
    private final MemberRepository members;
    private final MembershipService memberships;
    private final QrTokenService qr;
    private final LoyaltyService loyalty;
    private final Clock clock;

    public AttendanceService(AttendanceRepository repo, MemberRepository members, MembershipService memberships,
            QrTokenService qr, LoyaltyService loyalty, Clock clock) {
        this.repo = repo;
        this.members = members;
        this.memberships = memberships;
        this.qr = qr;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    /** Finds the member from a QR token, card number, or (manual) phone / membership number / id. */
    public Member identify(EntryMethod method, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("مقدار شناسایی خالی است");
        }
        String v = Digits.toLatin(value.trim());
        return switch (method) {
            case QR -> members.findById(qr.verify(v)).orElseThrow(() -> BusinessException.notFound("عضو"));
            case CARD -> members.findByCardNo(v).orElseThrow(() -> new BusinessException("کارتی با این شماره ثبت نشده است"));
            case MANUAL -> {
                String phone = Phones.normalize(v);
                if (Phones.isValid(phone)) {
                    yield members.findByPhone(phone).orElseThrow(() -> BusinessException.notFound("عضو"));
                }
                yield members.findByMembershipNo(v).orElseThrow(() -> BusinessException.notFound("عضو"));
            }
        };
    }

    /** Kiosk behaviour: checks the member in, or out if they are already inside. */
    @Transactional
    public ScanResult scan(EntryMethod method, String value, Long staffId) {
        Member m = identify(method, value);
        var open = repo.findFirstByMemberIdAndCheckOutAtIsNullOrderByIdDesc(m.getId());
        if (open.isPresent()) {
            return checkOut(open.get());
        }
        return checkIn(m, method, staffId);
    }

    @Transactional
    public ScanResult checkIn(Member m, EntryMethod method, Long staffId) {
        if (!m.getUser().isActive()) {
            throw new BusinessException("حساب این عضو غیرفعال است");
        }
        if (repo.findFirstByMemberIdAndCheckOutAtIsNullOrderByIdDesc(m.getId()).isPresent()) {
            throw BusinessException.conflict(m.getFullName() + " هم‌اکنون در باشگاه حضور دارد");
        }
        Membership ms = memberships.usableToday(m.getId());
        if (ms == null) {
            throw new BusinessException(memberships.entryBlockReason(m.getId()));
        }
        if (ms.getSessionsTotal() != null) {
            ms.setSessionsUsed(ms.getSessionsUsed() + 1);
        }
        Attendance a = new Attendance();
        a.setMember(m);
        a.setMembership(ms);
        a.setCheckInAt(clock.instant());
        a.setMethod(method);
        a.setRecordedBy(staffId);
        repo.save(a);
        loyalty.onCheckIn(m);
        String msg = "ورود " + m.getFullName() + " ثبت شد";
        if (ms.sessionsRemaining() != null && ms.sessionsRemaining() <= 2) {
            msg += " — فقط " + ms.sessionsRemaining() + " جلسه باقی مانده است";
        }
        return new ScanResult(Action.CHECK_IN, AttendanceDto.of(a), m.getFullName(), m.getMembershipNo(),
                ms.getPlan().getName(), ms.getEndDate(), ms.sessionsRemaining(), msg);
    }

    @Transactional
    public ScanResult checkOut(Long attendanceId) {
        Attendance a = repo.findById(attendanceId).orElseThrow(() -> BusinessException.notFound("تردد"));
        if (a.getCheckOutAt() != null) {
            throw new BusinessException("خروج این تردد قبلاً ثبت شده است");
        }
        return checkOut(a);
    }

    private ScanResult checkOut(Attendance a) {
        a.setCheckOutAt(clock.instant());
        Member m = a.getMember();
        Membership ms = a.getMembership();
        return new ScanResult(Action.CHECK_OUT, AttendanceDto.of(a), m.getFullName(), m.getMembershipNo(),
                ms == null ? null : ms.getPlan().getName(), ms == null ? null : ms.getEndDate(),
                ms == null ? null : ms.sessionsRemaining(), "خروج " + m.getFullName() + " ثبت شد");
    }

    public List<AttendanceDto> present() {
        return repo.present().stream().map(AttendanceDto::of).toList();
    }

    public PageResponse<AttendanceDto> search(Long memberId, LocalDate from, LocalDate to, int page, int size) {
        ZoneId zone = clock.getZone();
        LocalDate f = from != null ? from : LocalDate.now(clock).minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now(clock);
        return PageResponse.of(repo.search(memberId, f.atStartOfDay(zone).toInstant(),
                t.plusDays(1).atStartOfDay(zone).toInstant(),
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "checkInAt"))),
                AttendanceDto::of);
    }

    /** Closes sessions left open from previous days (members who forgot to check out). */
    @Transactional
    public int closeStaleSessions() {
        var startOfToday = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        List<Attendance> stale = repo.openBefore(startOfToday);
        stale.forEach(a -> a.setCheckOutAt(a.getCheckInAt().plusSeconds(2 * 3600)));
        return stale.size();
    }
}
