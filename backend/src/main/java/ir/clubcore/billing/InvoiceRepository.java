package ir.clubcore.billing;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("""
            select i from Invoice i join fetch i.member m join fetch m.user
            where (:memberId is null or m.id = :memberId) and (:status is null or i.status = :status)
            """)
    Page<Invoice> search(Long memberId, InvoiceStatus status, Pageable pageable);

    List<Invoice> findByMemberIdOrderByIdDesc(Long memberId);

    long countByMemberIdAndStatus(Long memberId, InvoiceStatus status);

    long countByStatus(InvoiceStatus status);

    @Query("select coalesce(sum(i.total), 0) from Invoice i where i.status = 'PAID' and i.paidAt >= :from and i.paidAt < :to")
    long paidTotalBetween(Instant from, Instant to);

    @Query("select coalesce(sum(i.total), 0) from Invoice i where i.status = 'PAID' and i.member.id = :memberId")
    long paidTotalByMember(Long memberId);

    @Query(value = "select nextval('invoice_no_seq')", nativeQuery = true)
    long nextNumber();
}
