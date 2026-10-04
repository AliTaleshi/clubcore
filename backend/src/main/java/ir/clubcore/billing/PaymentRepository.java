package ir.clubcore.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ir.clubcore.billing.gateway.GatewayType;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Locks the payment so concurrent callbacks for the same transaction are processed one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gateway = :gateway and p.authority = :authority")
    Optional<Payment> lockByGatewayAndAuthority(GatewayType gateway, String authority);

    List<Payment> findByInvoiceIdOrderByIdDesc(Long invoiceId);
}
