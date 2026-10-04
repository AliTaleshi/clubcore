package ir.clubcore.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUserId(Long userId);

    /** Row lock that serializes per-member operations (check-in, purchase, loyalty spending). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(Long id);

    @Query("select m from Member m join fetch m.user")
    List<Member> findAllWithUser();

    Optional<Member> findByCardNo(String cardNo);

    Optional<Member> findByMembershipNo(String membershipNo);

    Optional<Member> findByReferralCode(String referralCode);

    @Query("select m from Member m where m.user.phone = :phone")
    Optional<Member> findByPhone(String phone);

    boolean existsByCardNo(String cardNo);

    @Query("""
            select m from Member m join m.user u
            where (:q is null or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or u.phone like concat('%', cast(:q as string), '%')
                   or m.membershipNo = :q or m.cardNo = :q or m.nationalCode = :q)
              and (:coachId is null or m.coach.id = :coachId)
            """)
    Page<Member> search(String q, Long coachId, Pageable pageable);

    List<Member> findByCoachId(Long coachId);

    long countByReferredById(Long memberId);

    long countByCreatedAtAfter(java.time.Instant after);

    @Query(value = "select nextval('membership_no_seq')", nativeQuery = true)
    long nextMembershipNo();
}
