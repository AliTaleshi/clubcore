import { expect, test } from '@playwright/test';
import { ACCOUNTANT, login, logout } from './helpers';

test('accountant sees finance but not reception tools, and URL access to them is redirected', async ({ page }) => {
  await login(page, ACCOUNTANT.phone, ACCOUNTANT.password);
  await expect(page).toHaveURL(/\/accounting/);
  const nav = page.getByRole('navigation');
  await expect(nav.getByRole('link', { name: 'حسابداری' })).toBeVisible();
  await expect(nav.getByRole('link', { name: 'تحلیل هوشمند (AI)' })).toBeVisible();
  await expect(nav.getByRole('link', { name: 'کیوسک ورود و خروج' })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: 'تنظیمات' })).toHaveCount(0);

  await page.goto('/kiosk');
  await expect(page).toHaveURL(/\/accounting/);

  await page.getByRole('link', { name: 'تحلیل هوشمند (AI)' }).click();
  await expect(page.getByRole('heading', { name: 'تحلیل هوشمند کسب‌وکار' })).toBeVisible();
  await expect(page.getByText('اعضای فعال')).toBeVisible();
  await logout(page);
});

test('member uses the AI assistant and cannot open staff pages', async ({ page }) => {
  // Demo member with a password (seeded)
  await login(page, '09121000001', 'Member@12345');
  await expect(page).toHaveURL(/\/me$/);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/me$/);

  await page.getByRole('link', { name: 'دستیار هوشمند' }).click();
  await page.getByRole('button', { name: 'پاک کردن گفتگو' }).click();
  await page.getByLabel('پیام').fill('برای تغذیه چه پیشنهادی داری؟');
  await page.getByRole('button', { name: 'ارسال' }).click();
  await expect(page.getByText(/پروتئین|تغذیه/).last()).toBeVisible({ timeout: 60_000 });
});
