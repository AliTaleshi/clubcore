package ir.clubcore.coaching;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberDto;
import ir.clubcore.member.MemberService;
import ir.clubcore.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class CoachingController {

    public record ProgramRequest(@NotBlank(message = "عنوان برنامه الزامی است") @Size(max = 150) String title,
            @NotBlank(message = "محتوای برنامه الزامی است") @Size(max = 20000) String content, boolean aiGenerated) {
    }

    private final WorkoutProgramRepository programs;
    private final MemberService members;
    private final CurrentUser currentUser;

    public CoachingController(WorkoutProgramRepository programs, MemberService members, CurrentUser currentUser) {
        this.programs = programs;
        this.members = members;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/coaching/trainees")
    @PreAuthorize("hasRole('COACH')")
    @Transactional(readOnly = true)
    public List<MemberDto> trainees() {
        return members.trainees(currentUser.id());
    }

    @GetMapping("/api/members/{memberId}/programs")
    @Transactional(readOnly = true)
    public List<WorkoutProgram> ofMember(@PathVariable Long memberId) {
        members.getAccessible(memberId);
        return programs.findByMemberIdOrderByIdDesc(memberId);
    }

    @PostMapping("/api/members/{memberId}/programs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','COACH','MEMBER')")
    @Transactional
    public WorkoutProgram create(@PathVariable Long memberId, @Valid @RequestBody ProgramRequest req) {
        Member m = members.getAccessible(memberId);
        WorkoutProgram p = new WorkoutProgram();
        p.setMemberId(m.getId());
        p.setCoachId(currentUser.is(Role.MEMBER) ? null : currentUser.id());
        p.setTitle(req.title().trim());
        p.setContent(req.content());
        p.setAiGenerated(req.aiGenerated());
        return programs.save(p);
    }

    @DeleteMapping("/api/programs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COACH','MEMBER')")
    @Transactional
    public void delete(@PathVariable Long id) {
        WorkoutProgram p = programs.findById(id).orElseThrow(() -> BusinessException.notFound("برنامه"));
        members.getAccessible(p.getMemberId());
        if (currentUser.is(Role.MEMBER) && p.getCoachId() != null) {
            throw new BusinessException("برنامه‌ای که مربی ثبت کرده فقط توسط مربی قابل حذف است");
        }
        programs.delete(p);
    }

    @GetMapping("/api/me/programs")
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public List<WorkoutProgram> mine() {
        return programs.findByMemberIdOrderByIdDesc(members.current().getId());
    }
}
