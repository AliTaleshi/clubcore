package ir.clubcore.setting;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettingController {

    private final SettingService service;

    public SettingController(SettingService service) {
        this.service = service;
    }

    @GetMapping("/api/public/gym-info")
    public Map<String, String> gymInfo() {
        return service.publicInfo();
    }

    @GetMapping("/api/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> all() {
        return service.all();
    }

    @PutMapping("/api/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> update(@RequestBody Map<String, String> values) {
        return service.update(values);
    }
}
