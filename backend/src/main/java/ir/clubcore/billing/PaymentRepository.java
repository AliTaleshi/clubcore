package ir.clubcore.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ir.clubcore.billing.gateway.GatewayType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayAndAuthority(GatewayType gateway, String authority);

    List<Payment> findByInvoiceIdOrderByIdDesc(Long invoiceId);
}
