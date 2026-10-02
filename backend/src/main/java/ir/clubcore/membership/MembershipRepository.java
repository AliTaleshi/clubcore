package ir.clubcore.membership;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByMemberIdOrderByIdDesc(Long memberId);

    List<Membership> findByMemberIdAndStatusIn(Long memberId, Collection<MembershipStatus> statuses);

    Optional<Membership> findByInvoiceId(Long invoiceId);

    @Query("""
            select ms from Membership ms where ms.member.id = :memberId and ms.status = 'ACTIVE'
              and ms.startDate <= :day and ms.endDate >= :day
              and (ms.sessionsTotal is null or ms.sessionsUsed < ms.sessionsTotal)
            order by ms.endDate asc
            """)
    List<Membership> usableOn(Long memberId, LocalDate day);

    @Query("select ms from Membership ms where ms.status = 'ACTIVE' and (ms.endDate < :today or (ms.sessionsTotal is not null and ms.sessionsUsed >= ms.sessionsTotal))")
    List<Membership> toExpire(LocalDate today);

    @Query("select ms from Membership ms join fetch ms.member m join fetch m.user where ms.status = 'ACTIVE' and ms.endDate = :day")
    List<Membership> endingOn(LocalDate day);

    @Query("select ms from Membership ms where ms.status = 'FROZEN'")
    List<Membership> frozen();

    @Query("select count(distinct ms.member.id) from Membership ms where ms.status in ('ACTIVE','FROZEN') and ms.endDate >= :today")
    long countActiveMembers(LocalDate today);

    @Query("select ms from Membership ms join fetch ms.member m join fetch m.user where ms.status = 'ACTIVE' and ms.endDate between :from and :to order by ms.endDate")
    List<Membership> expiringBetween(LocalDate from, LocalDate to);

    @Query("""
            select ms.plan.name, count(ms), coalesce(sum(ms.price - ms.discount), 0) from Membership ms
            where ms.status in ('ACTIVE','FROZEN','EXPIRED') and ms.createdAt >= :from and ms.createdAt < :to
            group by ms.plan.name order by count(ms) desc
            """)
    List<Object[]> salesByPlan(java.time.Instant from, java.time.Instant to);

    long countByMemberIdAndStatusIn(Long memberId, Collection<MembershipStatus> statuses);
}
