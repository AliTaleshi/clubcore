package ir.clubcore.user;

import java.time.Instant;

public record UserDto(Long id, String phone, String fullName, Role role, boolean active, Instant createdAt) {

    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getPhone(), u.getFullName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }
}
