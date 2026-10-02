import { expect, test } from '@playwright/test';
import { uniquePhone } from './helpers';

test('visitor registers, buys a plan through the payment gateway and gets an active membership with points', async ({ page }) => {
  const phone = uniquePhone();
  await page.goto('/register');
  await page.getByLabel('نام و نام خانوادگی').fill('مهمان آزمون');
  await page.getByLabel('شماره موبایل').fill(phone);
  await page.getByLabel('رمز عبور').fill('Member@123');
  await page.getByRole('button', { name: 'ثبت‌نام' }).click();

  await expect(page).toHaveURL(/\/me\/buy/);
  await expect(page.getByRole('heading', { name: 'خرید یا تمدید اشتراک' })).toBeVisible();
  const card = page.locator('.MuiCard-root', { hasText: 'یک ماهه نامحدود' });
  await card.getByRole('button', { name: 'انتخاب' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('مبلغ قابل پرداخت')).toBeVisible();
  await dialog.getByRole('button', { name: 'پرداخت و فعال‌سازی' }).click();

  // Simulated bank page (MOCK gateway)
  await expect(page).toHaveURL(/\/mock-gateway/);
  await expect(page.getByText('درگاه پرداخت آزمایشی')).toBeVisible();
  await page.getByRole('button', { name: 'پرداخت موفق' }).click();

  await expect(page).toHaveURL(/\/payment\/result\?status=PAID/);
  await expect(page.getByText('پرداخت با موفقیت انجام شد')).toBeVisible();
  await expect(page.getByText(/کد پیگیری/)).toBeVisible();
  await page.getByRole('link', { name: 'بازگشت به پنل' }).click();

  await expect(page.getByText('یک ماهه نامحدود')).toBeVisible();
  await expect(page.getByText('فعال', { exact: true })).toBeVisible();
  await expect(page.getByTestId('qr-box').locator('svg')).toBeVisible();

  await page.getByRole('link', { name: 'امتیازها و جوایز' }).click();
  // 1,500,000 Toman / 10,000 per point = 150 purchase points
  await expect(page.getByText('۱۵۰').first()).toBeVisible();
  await expect(page.getByText('خرید').first()).toBeVisible();

  await page.getByRole('link', { name: 'فاکتورهای من' }).click();
  await expect(page.getByText('پرداخت‌شده')).toBeVisible();
});

test('failed payment leaves the invoice payable from the invoices page', async ({ page }) => {
  const phone = uniquePhone();
  await page.goto('/register');
  await page.getByLabel('نام و نام خانوادگی').fill('مهمان دوم');
  await page.getByLabel('شماره موبایل').fill(phone);
  await page.getByLabel('رمز عبور').fill('Member@123');
  await page.getByRole('button', { name: 'ثبت‌نام' }).click();
  await page.locator('.MuiCard-root', { hasText: '۱۲ جلسه‌ای' }).getByRole('button', { name: 'انتخاب' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'پرداخت و فعال‌سازی' }).click();
  await page.getByRole('button', { name: 'انصراف از پرداخت' }).click();
  await expect(page.getByText('پرداخت ناموفق بود')).toBeVisible();

  await page.goto('/me/invoices');
  await expect(page.getByText('پرداخت‌نشده')).toBeVisible();
  await page.getByRole('button', { name: 'پرداخت آنلاین' }).click();
  await page.getByRole('button', { name: 'پرداخت موفق' }).click();
  await expect(page.getByText('پرداخت با موفقیت انجام شد')).toBeVisible();
});
