package ir.clubcore.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByRoleNotOrderByIdAsc(Role role);

    List<User> findByRoleAndActiveTrue(Role role);

    long countByRole(Role role);
}
