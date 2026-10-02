package ir.clubcore.ai;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    List<AiMessage> findTop20ByUserIdOrderByIdDesc(Long userId);

    @Modifying
    @Query("delete from AiMessage m where m.userId = :userId")
    void deleteByUserId(Long userId);
}
