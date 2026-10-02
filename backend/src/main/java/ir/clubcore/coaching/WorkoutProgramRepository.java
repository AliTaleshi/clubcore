package ir.clubcore.coaching;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutProgramRepository extends JpaRepository<WorkoutProgram, Long> {

    List<WorkoutProgram> findByMemberIdOrderByIdDesc(Long memberId);
}
