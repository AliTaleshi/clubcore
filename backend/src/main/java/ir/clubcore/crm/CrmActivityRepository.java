package ir.clubcore.crm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long> {

    List<CrmActivity> findByLeadIdOrderByIdDesc(Long leadId);

    List<CrmActivity> findByMemberIdOrderByIdDesc(Long memberId);
}
