package ir.clubcore.attendance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findFirstByMemberIdAndCheckOutAtIsNullOrderByIdDesc(Long memberId);

    @Query("select a from Attendance a join fetch a.member m join fetch m.user where a.checkOutAt is null order by a.checkInAt desc")
    List<Attendance> present();

    @Query("select a from Attendance a where a.checkOutAt is null and a.checkInAt < :before")
    List<Attendance> openBefore(Instant before);

    @Query("""
            select a from Attendance a join fetch a.member m join fetch m.user
            where (:memberId is null or m.id = :memberId) and a.checkInAt >= :from and a.checkInAt < :to
            """)
    Page<Attendance> search(Long memberId, Instant from, Instant to, Pageable pageable);

    List<Attendance> findByMemberIdAndCheckInAtAfterOrderByCheckInAtDesc(Long memberId, Instant after);

    long countByCheckInAtBetween(Instant from, Instant to);

    long countByCheckOutAtIsNull();

    @Query("select max(a.checkInAt) from Attendance a where a.member.id = :memberId")
    Instant lastVisit(Long memberId);

    long countByMemberIdAndCheckInAtBetween(Long memberId, Instant from, Instant to);

    long countByMemberId(Long memberId);

    /** Visits per hour of day (Tehran time) over a period, for the peak-hours chart. */
    @Query(value = """
            select extract(hour from check_in_at at time zone 'Asia/Tehran') as h, count(*) from attendances
            where check_in_at >= :from group by h order by h
            """, nativeQuery = true)
    List<Object[]> hourlyDistribution(Instant from);

    @Query(value = """
            select cast((check_in_at at time zone 'Asia/Tehran') as date) as d, count(*) from attendances
            where check_in_at >= :from group by d order by d
            """, nativeQuery = true)
    List<Object[]> dailyCounts(Instant from);
}
