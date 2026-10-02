package ir.clubcore.membership;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.member.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class MembershipController {

    public record PurchaseRequest(@NotNull Long memberId, @NotNull(message = "پلن را انتخاب کنید") Long planId,
            LocalDate startDate, String discountCode) {
    }

    public record SelfPurchaseRequest(@NotNull(message = "پلن را انتخاب کنید") Long planId, LocalDate startDate,
            String discountCode) {
    }

    private final MembershipService service;
    private final MemberService members;

    public MembershipController(MembershipService service, MemberService members) {
        this.service = service;
        this.members = members;
    }

    @GetMapping("/api/members/{memberId}/memberships")
    @Transactional(readOnly = true)
    public List<MembershipDto> ofMember(@PathVariable Long memberId) {
        members.getAccessible(memberId);
        return service.ofMember(memberId);
    }

    @GetMapping("/api/memberships/quote")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    @Transactional(readOnly = true)
    public MembershipService.Quote quote(@RequestParam Long memberId, @RequestParam Long planId,
            @RequestParam(required = false) String discountCode) {
        return service.quote(members.get(memberId), planId, discountCode);
    }

    @PostMapping("/api/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','ACCOUNTANT')")
    @Transactional
    public MembershipService.PurchaseResult purchase(@Valid @RequestBody PurchaseRequest req) {
        return service.purchase(members.get(req.memberId()), req.planId(), req.startDate(), req.discountCode());
    }

    @PostMapping("/api/memberships/{id}/freeze")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public MembershipDto freeze(@PathVariable Long id) {
        return service.freeze(id);
    }

    @PostMapping("/api/memberships/{id}/unfreeze")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public MembershipDto unfreeze(@PathVariable Long id) {
        return service.unfreeze(id);
    }

    @PostMapping("/api/memberships/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public MembershipDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    // ------------------------------------------------------------ member self-service

    @GetMapping("/api/me/memberships")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public List<MembershipDto> mine() {
        return service.ofMember(members.current().getId());
    }

    @GetMapping("/api/me/memberships/quote")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public MembershipService.Quote myQuote(@RequestParam Long planId,
            @RequestParam(required = false) String discountCode) {
        return service.quote(members.current(), planId, discountCode);
    }

    @PostMapping("/api/me/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional
    public MembershipService.PurchaseResult buy(@Valid @RequestBody SelfPurchaseRequest req) {
        return service.purchase(members.current(), req.planId(), req.startDate(), req.discountCode());
    }
}
