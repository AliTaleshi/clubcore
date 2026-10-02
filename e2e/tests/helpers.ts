import { expect, type Page } from '@playwright/test';

export const ADMIN = { phone: process.env.ADMIN_PHONE ?? '09120000000', password: process.env.ADMIN_PASSWORD ?? 'Admin@12345' };
// Demo staff accounts created by SEED_DEMO=true.
export const RECEPTION = { phone: '09120000001', password: 'Staff@12345' };
export const ACCOUNTANT = { phone: '09120000002', password: 'Staff@12345' };

let seq = 0;
/** Unique mobile number per test run. */
export function uniquePhone(): string {
  seq += 1;
  return `0915${String((Date.now() + seq) % 10_000_000).padStart(7, '0')}`;
}

export async function login(page: Page, phone: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('شماره موبایل').fill(phone);
  await page.getByLabel('رمز عبور').fill(password);
  await page.getByRole('button', { name: 'ورود', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: 'حساب کاربری' }).click();
  await page.getByRole('menuitem', { name: 'خروج' }).click();
  await expect(page).toHaveURL(/\/login/);
}
