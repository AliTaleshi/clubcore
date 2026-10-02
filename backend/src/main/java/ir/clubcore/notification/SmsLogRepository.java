package ir.clubcore.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    List<SmsLog> findTop20ByPhoneOrderByIdDesc(String phone);
}
