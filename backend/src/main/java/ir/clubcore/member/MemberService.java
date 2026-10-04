package ir.clubcore.member;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Paging;
import ir.clubcore.common.Codes;
import ir.clubcore.common.Digits;
import ir.clubcore.common.NationalCodes;
import ir.clubcore.common.PageResponse;
import ir.clubcore.common.Phones;
import ir.clubcore.config.CurrentUser;
import ir.clubcore.notification.NotificationService;
import ir.clubcore.user.Role;
import ir.clubcore.user.User;
import ir.clubcore.user.UserRepository;
import ir.clubcore.user.UserService;

@Service
public class MemberService {

    private final MemberRepository members;
    private final UserService userService;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final NotificationService notifications;

    public MemberService(MemberRepository members, UserService userService, UserRepository users,
            CurrentUser currentUser, NotificationService notifications) {
        this.members = members;
        this.userService = userService;
        this.users = users;
        this.currentUser = currentUser;
        this.notifications = notifications;
    }

    @Transactional
    public Member create(MemberRequest req) {
        validate(req, null);
        User user = userService.create(req.phone(), req.fullName(), Role.MEMBER, req.password());
        Member m = new Member();
        m.setUser(user);
        m.setMembershipNo(String.valueOf(members.nextMembershipNo()));
        m.setReferralCode(newReferralCode());
        apply(m, req);
        if (req.referralCode() != null && !req.referralCode().isBlank()) {
            m.setReferredBy(byReferralCode(req.referralCode()));
        }
        members.save(m);
        notifications.notify(user.getId(), "به باشگاه خوش آمدید",
                "عضویت شما با شماره " + m.getMembershipNo() + " ثبت شد.");
        return m;
    }

    @Transactional
    public Member selfRegister(String fullName, String phone, String password, String referralCode) {
        return create(new MemberRequest(fullName, Phones.normalize(phone), password, null, null, null, null, null,
                null, null, referralCode, null, null, null));
    }

    @Transactional
    public MemberDto update(Long id, MemberRequest req) {
        Member m = get(id);
        validate(req, m);
        String phone = Phones.normalize(req.phone());
        if (!phone.equals(m.getPhone()) && users.existsByPhone(phone)) {
            throw BusinessException.conflict("این شماره موبایل قبلاً ثبت شده است");
        }
        m.getUser().setPhone(phone);
        m.getUser().setFullName(req.fullName().trim());
        if (req.active() != null) {
            m.getUser().setActive(req.active());
            if (!req.active()) {
                userService.revokeSessions(m.getUser().getId());
            }
        }
        apply(m, req);
        return MemberDto.of(m);
    }

    /** Fields a member may change on their own profile. */
    @Transactional
    public MemberDto updateOwnProfile(Long userId, MemberRequest req) {
        Member m = byUserId(userId);
        m.getUser().setFullName(req.fullName().trim());
        m.setAddress(req.address());
        m.setEmergencyPhone(blankToNull(req.emergencyPhone()));
        m.setGoal(req.goal());
        if (req.birthDate() != null) {
            m.setBirthDate(req.birthDate());
        }
        if (req.gender() != null) {
            m.setGender(req.gender());
        }
        return MemberDto.of(m);
    }

    public PageResponse<MemberDto> search(String q, int page, int size) {
        Long coachFilter = currentUser.is(Role.COACH) ? currentUser.id() : null;
        String query = q == null || q.isBlank() ? null : Digits.toLatin(q.trim());
        if (query != null && query.matches("^(\\+98|0098|98)?0?9\\d{9}$")) {
            query = Phones.normalize(query);
        }
        return PageResponse.of(members.search(query, coachFilter,
                Paging.of(page, size, 100, Sort.by(Sort.Direction.DESC, "id"))), MemberDto::of);
    }

    public Member get(Long id) {
        return members.findById(id).orElseThrow(() -> BusinessException.notFound("عضو"));
    }

    /** Loads a member enforcing that coaches only see their trainees and members only themselves. */
    public Member getAccessible(Long id) {
        Member m = get(id);
        if (currentUser.is(Role.MEMBER) && !m.getUser().getId().equals(currentUser.id())) {
            throw BusinessException.forbidden();
        }
        if (currentUser.is(Role.COACH) && (m.getCoach() == null || !m.getCoach().getId().equals(currentUser.id()))) {
            throw BusinessException.forbidden();
        }
        return m;
    }

    public Member byUserId(Long userId) {
        return members.findByUserId(userId).orElseThrow(() -> BusinessException.notFound("پروفایل عضو"));
    }

    public Member current() {
        return byUserId(currentUser.id());
    }

    public List<MemberDto> trainees(Long coachId) {
        return members.findByCoachId(coachId).stream().map(MemberDto::of).toList();
    }

    public long referralCount(Long memberId) {
        return members.countByReferredById(memberId);
    }

    private Member byReferralCode(String code) {
        return members.findByReferralCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("کد معرف نامعتبر است"));
    }

    private void validate(MemberRequest req, Member existing) {
        if (req.nationalCode() != null && !req.nationalCode().isBlank() && !NationalCodes.isValid(req.nationalCode())) {
            throw new BusinessException("کد ملی نامعتبر است");
        }
        String card = blankToNull(req.cardNo());
        if (card != null && (existing == null || !card.equals(existing.getCardNo())) && members.existsByCardNo(card)) {
            throw BusinessException.conflict("این شماره کارت به عضو دیگری اختصاص دارد");
        }
        if (req.coachId() != null) {
            User coach = userService.get(req.coachId());
            if (coach.getRole() != Role.COACH) {
                throw new BusinessException("مربی انتخاب‌شده معتبر نیست");
            }
        }
    }

    private void apply(Member m, MemberRequest req) {
        m.setNationalCode(blankToNull(req.nationalCode()));
        m.setGender(req.gender());
        m.setBirthDate(req.birthDate());
        m.setAddress(req.address());
        m.setEmergencyPhone(blankToNull(req.emergencyPhone()));
        m.setCardNo(blankToNull(req.cardNo()));
        m.setCoach(req.coachId() == null ? null : userService.get(req.coachId()));
        m.setGoal(req.goal());
        m.setNotes(req.notes());
    }

    private String newReferralCode() {
        String code;
        do {
            code = Codes.random(6);
        } while (members.findByReferralCode(code).isPresent());
        return code;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : Digits.toLatin(s.trim());
    }
}
