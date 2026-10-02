package ir.clubcore.plan;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class PlanController {

    public record PlanRequest(@NotBlank(message = "نام پلن الزامی است") @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(value = 1, message = "مدت پلن باید حداقل ۱ روز باشد") @Max(730) int durationDays,
            @Min(value = 1, message = "تعداد جلسات باید مثبت باشد") Integer sessionLimit,
            @Min(value = 0, message = "قیمت نمی‌تواند منفی باشد") long price,
            @Min(0) @Max(365) int maxFreezeDays,
            Boolean active) {
    }

    private final PlanRepository repo;

    public PlanController(PlanRepository repo) {
        this.repo = repo;
    }

    /** Active plans; public so the landing / registration page can show prices. */
    @GetMapping("/api/public/plans")
    public List<Plan> publicPlans() {
        return repo.findByActiveTrueOrderByPriceAsc();
    }

    @GetMapping("/api/plans")
    public List<Plan> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? repo.findAllByOrderByActiveDescPriceAsc() : repo.findByActiveTrueOrderByPriceAsc();
    }

    @PostMapping("/api/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Plan create(@Valid @RequestBody PlanRequest req) {
        Plan p = new Plan();
        apply(p, req);
        return repo.save(p);
    }

    @PutMapping("/api/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Plan update(@PathVariable Long id, @Valid @RequestBody PlanRequest req) {
        Plan p = repo.findById(id).orElseThrow(() -> BusinessException.notFound("پلن"));
        apply(p, req);
        return repo.save(p);
    }

    private static void apply(Plan p, PlanRequest req) {
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setDurationDays(req.durationDays());
        p.setSessionLimit(req.sessionLimit());
        p.setPrice(req.price());
        p.setMaxFreezeDays(req.maxFreezeDays());
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }
}
