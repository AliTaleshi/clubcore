package ir.clubcore.loyalty;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    @Query("select coalesce(sum(t.points), 0) from LoyaltyTransaction t where t.memberId = :memberId")
    long balance(Long memberId);

    /** Points ever earned; redemptions do not lower the tier. */
    @Query("select coalesce(sum(t.points), 0) from LoyaltyTransaction t where t.memberId = :memberId and t.points > 0 and t.reason <> 'REFUND'")
    long lifetime(Long memberId);

    boolean existsByMemberIdAndReasonAndReference(Long memberId, LoyaltyReason reason, String reference);

    List<LoyaltyTransaction> findTop50ByMemberIdOrderByIdDesc(Long memberId);

    @Query("""
            select t.memberId, sum(t.points) from LoyaltyTransaction t
            where t.points > 0 and t.createdAt >= :since group by t.memberId order by sum(t.points) desc limit 10
            """)
    List<Object[]> leaderboard(Instant since);

    @Query("select coalesce(sum(t.points), 0) from LoyaltyTransaction t where t.points > 0")
    long totalIssued();

    @Query("select coalesce(-sum(t.points), 0) from LoyaltyTransaction t where t.reason = 'REDEEM'")
    long totalRedeemed();
}
