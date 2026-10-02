package ir.clubcore.notification;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void notify(Long userId, String title, String body) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(body);
        repo.save(n);
    }

    public List<Notification> list(Long userId) {
        return repo.findTop50ByUserIdOrderByIdDesc(userId);
    }

    public Map<String, Long> unread(Long userId) {
        return Map.of("unread", repo.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> BusinessException.notFound("اعلان"));
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId);
    }
}
