package ir.clubcore.crm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.PageResponse;
import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.MemberDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/crm")
@PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
public class CrmController {

    public record ActivityRequest(Long leadId, Long memberId, @NotNull ActivityType type,
            @NotBlank(message = "متن فعالیت الزامی است") @Size(max = 2000) String content) {
    }

    public record CampaignRequest(@NotBlank(message = "عنوان کمپین الزامی است") @Size(max = 150) String title,
            @NotNull Segment segment, @NotBlank(message = "متن پیامک الزامی است") @Size(max = 500) String message) {
    }

    private final CrmService service;
    private final CurrentUser currentUser;

    public CrmController(CrmService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/leads")
    public PageResponse<Lead> leads(@RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(status, q, page, size);
    }

    @GetMapping("/leads/due")
    public List<Lead> due() {
        return service.dueToday();
    }

    @GetMapping("/leads/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/leads/{id}")
    public Map<String, Object> lead(@PathVariable Long id) {
        return Map.of("lead", service.get(id), "activities", service.leadActivities(id));
    }

    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    public Lead create(@Valid @RequestBody CrmService.LeadRequest req) {
        return service.create(req, currentUser.id());
    }

    @PutMapping("/leads/{id}")
    public Lead update(@PathVariable Long id, @Valid @RequestBody CrmService.LeadRequest req) {
        return service.update(id, req, currentUser.id());
    }

    @PostMapping("/leads/{id}/convert")
    public MemberDto convert(@PathVariable Long id) {
        return service.convert(id, currentUser.id());
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public CrmActivity addActivity(@Valid @RequestBody ActivityRequest req) {
        return service.log(req.leadId(), req.memberId(), req.type(), req.content().trim(), currentUser.id());
    }

    @GetMapping("/members/{memberId}/activities")
    public List<CrmActivity> memberActivities(@PathVariable Long memberId) {
        return service.memberActivities(memberId);
    }

    @GetMapping("/segments")
    public List<Map<String, String>> segments() {
        return Arrays.stream(Segment.values()).map(s -> Map.of("key", s.name(), "title", s.title())).toList();
    }

    @GetMapping("/segments/{segment}/members")
    @Transactional(readOnly = true)
    public List<MemberDto> segmentMembers(@PathVariable Segment segment) {
        return service.segmentMembers(segment);
    }

    @GetMapping("/campaigns")
    public List<Campaign> campaigns() {
        return service.campaigns();
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Campaign sendCampaign(@Valid @RequestBody CampaignRequest req) {
        return service.sendCampaign(req.title().trim(), req.segment(), req.message().trim(), currentUser.id());
    }
}
