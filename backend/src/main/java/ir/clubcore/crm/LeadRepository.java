package ir.clubcore.crm;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    @Query("""
            select l from Lead l where (:status is null or l.status = :status)
              and (:q is null or lower(l.fullName) like lower(concat('%', cast(:q as string), '%')) or l.phone like concat('%', cast(:q as string), '%'))
            """)
    Page<Lead> search(LeadStatus status, String q, Pageable pageable);

    @Query("select l from Lead l where l.followUpDate <= :day and l.status not in ('CONVERTED','LOST') order by l.followUpDate")
    List<Lead> dueOn(LocalDate day);

    @Query("select l.status, count(l) from Lead l group by l.status")
    List<Object[]> countByStatus();

    @Query("select l.source, count(l), sum(case when l.status = 'CONVERTED' then 1 else 0 end) from Lead l group by l.source")
    List<Object[]> sourceStats();
}
