package ir.clubcore.plan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderByPriceAsc();

    List<Plan> findAllByOrderByActiveDescPriceAsc();
}
