import { describe, expect, it } from 'vitest';
import { faDigits, formatDate, formatMoney, formatNumber, isValidNationalCode, isValidPhone, jalaliMonthKey, parseAmount,
  latinDigits, normalizePhone, toIsoDate } from './format';

describe('format utils', () => {
  it('converts digits both ways', () => {
    expect(faDigits(1405)).toBe('۱۴۰۵');
    expect(faDigits('A12')).toBe('A۱۲');
    expect(faDigits(null)).toBe('');
    expect(latinDigits('۰۹۱۲٣٤٥')).toBe('0912345');
  });

  it('formats money in Toman with Persian thousand separators', () => {
    expect(formatNumber(1500000)).toBe('۱٬۵۰۰٬۰۰۰');
    expect(formatMoney(250000)).toBe('۲۵۰٬۰۰۰ تومان');
    expect(formatMoney(null)).toBe('—');
  });

  it('formats Gregorian ISO dates as Jalali', () => {
    expect(formatDate('2026-03-21')).toBe('۱۴۰۵/۰۱/۰۱');
    expect(formatDate('2026-10-02')).toBe('۱۴۰۵/۰۷/۱۰');
    expect(formatDate(null)).toBe('—');
    expect(jalaliMonthKey('2026-10-02')).toBe('1405/07');
  });

  it('produces local ISO dates for the API', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('normalizes and validates Iranian mobile numbers', () => {
    expect(normalizePhone('+98 912 345 6789')).toBe('09123456789');
    expect(normalizePhone('۹۱۲۳۴۵۶۷۸۹')).toBe('09123456789');
    expect(isValidPhone('09123456789')).toBe(true);
    expect(isValidPhone('0912345678')).toBe(false);
    expect(isValidPhone('08123456789')).toBe(false);
  });

  it('validates national code checksum', () => {
    expect(isValidNationalCode('0499370899')).toBe(true);
    expect(isValidNationalCode('۰۴۹۹۳۷۰۸۹۹')).toBe(true);
    expect(isValidNationalCode('0499370898')).toBe(false);
    expect(isValidNationalCode('1111111111')).toBe(false);
  });
});

describe('parseAmount', () => {
  it('accepts separators, spaces and Persian digits', () => {
    expect(parseAmount('1500000')).toBe(1500000);
    expect(parseAmount('1,500,000')).toBe(1500000);
    expect(parseAmount('۱٬۵۰۰٬۰۰۰')).toBe(1500000);
    expect(parseAmount(' 1 500 000 ')).toBe(1500000);
    expect(parseAmount('0')).toBe(0);
  });

  it('rejects anything that is not a whole number instead of returning NaN', () => {
    expect(parseAmount('')).toBeNull();
    expect(parseAmount('abc')).toBeNull();
    expect(parseAmount('12.5')).toBeNull();
    expect(parseAmount('-5')).toBeNull();
    expect(parseAmount('99999999999999999999')).toBeNull();
  });

  it('allows negatives only when asked', () => {
    expect(parseAmount('-50', { allowNegative: true })).toBe(-50);
    expect(parseAmount('−۵۰', { allowNegative: true })).toBe(-50);
  });
});
