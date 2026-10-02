package ir.clubcore.notification;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.config.CurrentUser;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Notification> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return service.unread(currentUser.id());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        service.markRead(currentUser.id(), id);
    }

    @PostMapping("/read-all")
    public void readAll() {
        service.markAllRead(currentUser.id());
    }
}
