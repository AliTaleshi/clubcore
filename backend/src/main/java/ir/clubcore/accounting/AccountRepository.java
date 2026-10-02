package ir.clubcore.accounting;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    List<Account> findAllByOrderByCodeAsc();
}
