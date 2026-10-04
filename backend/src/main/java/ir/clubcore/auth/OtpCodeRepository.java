package ir.clubcore.auth;

import java.util.Optional;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByPhoneOrderByIdDesc(String phone);

    @Modifying
    @Query("delete from OtpCode o where o.expiresAt < :before")
    int purge(Instant before);
}
