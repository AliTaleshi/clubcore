import { format, parseISO, differenceInCalendarDays } from 'date-fns-jalali';

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';

/** Renders Latin digits as Persian digits. */
export function faDigits(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).replace(/\d/g, (d) => FA_DIGITS[Number(d)]);
}

/** Converts Persian/Arabic digits typed by users back to Latin digits. */
export function latinDigits(value: string): string {
  return value
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
}

export function formatNumber(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return faDigits(Math.round(n).toLocaleString('en-US').replace(/,/g, '٬'));
}

export function formatMoney(n: number | null | undefined): string {
  if (n === null || n === undefined) return '—';
  return `${formatNumber(n)} تومان`;
}

function toDate(value: string | Date): Date {
  return typeof value === 'string' ? parseISO(value) : value;
}

/** Jalali date, e.g. ۱۴۰۵/۰۷/۱۰ */
export function formatDate(value: string | Date | null | undefined): string {
  if (!value) return '—';
  return faDigits(format(toDate(value), 'yyyy/MM/dd'));
}

export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '—';
  return faDigits(format(toDate(value), 'yyyy/MM/dd - HH:mm'));
}

export function formatTime(value: string | Date | null | undefined): string {
  if (!value) return '—';
  return faDigits(format(toDate(value), 'HH:mm'));
}

/** Long Jalali date, e.g. «۱۰ مهر ۱۴۰۵» */
export function formatDateLong(value: string | Date | null | undefined): string {
  if (!value) return '—';
  return faDigits(format(toDate(value), 'd MMMM yyyy'));
}

export function jalaliMonthKey(value: string | Date): string {
  return format(toDate(value), 'yyyy/MM');
}

export function daysUntil(value: string): number {
  return differenceInCalendarDays(parseISO(value), new Date());
}

/** ISO yyyy-MM-dd for API calls (Gregorian). */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function normalizePhone(raw: string): string {
  let s = latinDigits(raw).replace(/[\s-]/g, '');
  if (s.startsWith('+98')) s = '0' + s.slice(3);
  else if (s.startsWith('0098')) s = '0' + s.slice(4);
  else if (/^9\d{9}$/.test(s)) s = '0' + s;
  return s;
}

export function isValidPhone(raw: string): boolean {
  return /^09\d{9}$/.test(normalizePhone(raw));
}

export function isValidNationalCode(raw: string): boolean {
  const c = latinDigits(raw);
  if (!/^\d{10}$/.test(c) || /^(\d)\1{9}$/.test(c)) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) sum += Number(c[i]) * (10 - i);
  const rem = sum % 11;
  const check = Number(c[9]);
  return rem < 2 ? check === rem : check === 11 - rem;
}
