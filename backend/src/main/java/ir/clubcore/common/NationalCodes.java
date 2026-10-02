package ir.clubcore.common;

/** Iranian national code (کد ملی) checksum validation. */
public final class NationalCodes {

    private NationalCodes() {
    }

    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String c = Digits.toLatin(code);
        if (!c.matches("\\d{10}") || c.chars().distinct().count() == 1) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (c.charAt(i) - '0') * (10 - i);
        }
        int rem = sum % 11;
        int check = c.charAt(9) - '0';
        return rem < 2 ? check == rem : check == 11 - rem;
    }
}
