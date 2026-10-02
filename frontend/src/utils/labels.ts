import type { ChipProps } from '@mui/material';

export type Role = 'ADMIN' | 'RECEPTIONIST' | 'ACCOUNTANT' | 'COACH' | 'MEMBER';

export const roleLabels: Record<Role, string> = {
  ADMIN: 'مدیر',
  RECEPTIONIST: 'پذیرش',
  ACCOUNTANT: 'حسابدار',
  COACH: 'مربی',
  MEMBER: 'عضو',
};

type Labeled = { label: string; color: ChipProps['color'] };

export const membershipStatus: Record<string, Labeled> = {
  PENDING_PAYMENT: { label: 'در انتظار پرداخت', color: 'warning' },
  ACTIVE: { label: 'فعال', color: 'success' },
  FROZEN: { label: 'فریز', color: 'info' },
  EXPIRED: { label: 'منقضی', color: 'default' },
  CANCELLED: { label: 'لغو شده', color: 'error' },
};

export const invoiceStatus: Record<string, Labeled> = {
  UNPAID: { label: 'پرداخت‌نشده', color: 'warning' },
  PAID: { label: 'پرداخت‌شده', color: 'success' },
  CANCELLED: { label: 'لغو شده', color: 'default' },
};

export const paymentStatus: Record<string, Labeled> = {
  PENDING: { label: 'در انتظار', color: 'warning' },
  PAID: { label: 'موفق', color: 'success' },
  FAILED: { label: 'ناموفق', color: 'error' },
};

export const paymentMethod: Record<string, string> = {
  ONLINE: 'اینترنتی',
  CASH: 'نقدی',
  POS: 'کارتخوان',
  FREE: 'رایگان',
};

export const entryMethod: Record<string, string> = { QR: 'کد QR', CARD: 'کارت', MANUAL: 'دستی' };

export const leadStatus: Record<string, Labeled> = {
  NEW: { label: 'جدید', color: 'info' },
  CONTACTED: { label: 'تماس گرفته شد', color: 'primary' },
  TRIAL: { label: 'جلسه آزمایشی', color: 'secondary' },
  CONVERTED: { label: 'عضو شد', color: 'success' },
  LOST: { label: 'از دست رفته', color: 'default' },
};

export const activityType: Record<string, string> = {
  CALL: 'تماس تلفنی',
  SMS: 'پیامک',
  VISIT: 'مراجعه حضوری',
  NOTE: 'یادداشت',
  EMAIL: 'ایمیل',
};

export const accountType: Record<string, string> = {
  ASSET: 'دارایی',
  LIABILITY: 'بدهی',
  EQUITY: 'سرمایه',
  INCOME: 'درآمد',
  EXPENSE: 'هزینه',
};

export const rewardType: Record<string, string> = {
  DISCOUNT_PERCENT: 'تخفیف درصدی',
  DISCOUNT_AMOUNT: 'تخفیف مبلغی',
  GIFT: 'هدیه',
};

export const loyaltyReason: Record<string, string> = {
  CHECKIN: 'حضور در باشگاه',
  PURCHASE: 'خرید',
  REFERRAL: 'معرفی دوستان',
  REDEEM: 'دریافت جایزه',
  ADJUST: 'اصلاح دستی',
  BONUS: 'پاداش',
  REFUND: 'برگشت',
};

export const tierColors: Record<string, string> = { BRONZE: '#b45309', SILVER: '#64748b', GOLD: '#ca8a04' };

export const churnLevel: Record<string, Labeled> = {
  LOW: { label: 'کم', color: 'success' },
  MEDIUM: { label: 'متوسط', color: 'warning' },
  HIGH: { label: 'زیاد', color: 'error' },
};

export const gender: Record<string, string> = { MALE: 'آقا', FEMALE: 'خانم' };
