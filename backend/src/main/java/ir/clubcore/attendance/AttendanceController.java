package ir.clubcore.attendance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.PageResponse;
import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
public class AttendanceController {

    public record ScanRequest(@NotNull EntryMethod method, @NotBlank(message = "مقدار شناسایی الزامی است") String value) {
    }

    private final AttendanceService service;
    private final MemberService members;
    private final CurrentUser currentUser;

    public AttendanceController(AttendanceService service, MemberService members, CurrentUser currentUser) {
        this.service = service;
        this.members = members;
        this.currentUser = currentUser;
    }

    /** Toggle check-in / check-out (kiosk, reception scanner). */
    @PostMapping("/api/attendance/scan")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public AttendanceService.ScanResult scan(@Valid @RequestBody ScanRequest req) {
        return service.scan(req.method(), req.value(), currentUser.id());
    }

    @PostMapping("/api/attendance/check-in/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @Transactional
    public AttendanceService.ScanResult checkIn(@PathVariable Long memberId) {
        return service.checkIn(members.get(memberId), EntryMethod.MANUAL, currentUser.id());
    }

    @PostMapping("/api/attendance/{id}/check-out")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public AttendanceService.ScanResult checkOut(@PathVariable Long id) {
        return service.checkOut(id);
    }

    @GetMapping("/api/attendance/present")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','COACH')")
    @Transactional(readOnly = true)
    public List<AttendanceDto> present() {
        return service.present();
    }

    @GetMapping("/api/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','COACH')")
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDto> search(@RequestParam(required = false) Long memberId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (memberId != null) {
            members.getAccessible(memberId);
        } else if (currentUser.is(ir.clubcore.user.Role.COACH)) {
            throw ir.clubcore.common.BusinessException.forbidden();
        }
        return service.search(memberId, from, to, page, size);
    }

    @GetMapping("/api/me/attendance")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDto> mine(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(members.current().getId(), from, to, page, size);
    }
}
