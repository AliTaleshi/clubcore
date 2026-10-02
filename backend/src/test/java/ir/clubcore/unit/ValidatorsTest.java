package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ir.clubcore.common.Digits;
import ir.clubcore.common.NationalCodes;
import ir.clubcore.common.Phones;

class ValidatorsTest {

    @Test
    void normalizesPhonesFromPersianDigitsAndCountryCode() {
        assertThat(Phones.normalize("۰۹۱۲۳۴۵۶۷۸۹")).isEqualTo("09123456789");
        assertThat(Phones.normalize("+989123456789")).isEqualTo("09123456789");
        assertThat(Phones.normalize("00989123456789")).isEqualTo("09123456789");
        assertThat(Phones.normalize("9123456789")).isEqualTo("09123456789");
        assertThat(Phones.normalize("0912-345 6789")).isEqualTo("09123456789");
    }

    @Test
    void validatesPhoneShape() {
        assertThat(Phones.isValid("09123456789")).isTrue();
        assertThat(Phones.isValid("0912345678")).isFalse();
        assertThat(Phones.isValid("08123456789")).isFalse();
        assertThat(Phones.isValid(null)).isFalse();
    }

    @Test
    void convertsArabicAndPersianDigits() {
        assertThat(Digits.toLatin("۱۲۳٤٥٦")).isEqualTo("123456");
        assertThat(Digits.toLatin("abc")).isEqualTo("abc");
    }

    @Test
    void validatesNationalCodeChecksum() {
        assertThat(NationalCodes.isValid("0499370899")).isTrue();
        assertThat(NationalCodes.isValid("۰۴۹۹۳۷۰۸۹۹")).isTrue();
        assertThat(NationalCodes.isValid("0499370898")).isFalse();
        assertThat(NationalCodes.isValid("1111111111")).isFalse();
        assertThat(NationalCodes.isValid("12345")).isFalse();
    }
}
