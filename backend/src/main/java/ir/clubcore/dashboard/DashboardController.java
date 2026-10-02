package ir.clubcore.dashboard;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final KpiService kpis;

    public DashboardController(KpiService kpis) {
        this.kpis = kpis;
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    public Map<String, Object> dashboard() {
        return kpis.dashboard();
    }
}
