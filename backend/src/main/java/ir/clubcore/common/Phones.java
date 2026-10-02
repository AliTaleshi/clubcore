package ir.clubcore.common;

import java.util.regex.Pattern;

public final class Phones {

    public static final String REGEX = "^09\\d{9}$";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private Phones() {
    }

    /** Converts Persian/Arabic digits and +98/0098 prefixes to the canonical 09xxxxxxxxx form. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = Digits.toLatin(raw).replaceAll("[\\s-]", "");
        if (s.startsWith("+98")) {
            s = "0" + s.substring(3);
        } else if (s.startsWith("0098")) {
            s = "0" + s.substring(4);
        } else if (s.startsWith("98") && s.length() == 12) {
            s = "0" + s.substring(2);
        } else if (s.startsWith("9") && s.length() == 10) {
            s = "0" + s;
        }
        return s;
    }

    public static boolean isValid(String phone) {
        return phone != null && PATTERN.matcher(phone).matches();
    }
}
