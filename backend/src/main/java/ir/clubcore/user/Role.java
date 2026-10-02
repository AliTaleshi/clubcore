package ir.clubcore.user;

public enum Role {
    ADMIN, RECEPTIONIST, ACCOUNTANT, COACH, MEMBER;

    public boolean isStaff() {
        return this != MEMBER;
    }
}
