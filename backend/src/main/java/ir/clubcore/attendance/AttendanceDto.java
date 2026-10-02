package ir.clubcore.attendance;

import java.time.Instant;

public record AttendanceDto(Long id, Long memberId, String memberName, String membershipNo, Instant checkInAt,
        Instant checkOutAt, EntryMethod method) {

    public static AttendanceDto of(Attendance a) {
        return new AttendanceDto(a.getId(), a.getMember().getId(), a.getMember().getFullName(),
                a.getMember().getMembershipNo(), a.getCheckInAt(), a.getCheckOutAt(), a.getMethod());
    }
}
