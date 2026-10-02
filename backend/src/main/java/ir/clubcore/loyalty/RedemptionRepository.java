package ir.clubcore.loyalty;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    Optional<Redemption> findByCode(String code);

    List<Redemption> findByMemberIdOrderByIdDesc(Long memberId);

    Optional<Redemption> findByInvoiceId(Long invoiceId);
}
