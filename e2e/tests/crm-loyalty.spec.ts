import { expect, test } from '@playwright/test';
import { RECEPTION, login, uniquePhone } from './helpers';

test('receptionist registers a lead, logs a call and converts it to a member', async ({ page }) => {
  const phone = uniquePhone();
  const name = `سرنخ ${phone.slice(-4)}`;
  await login(page, RECEPTION.phone, RECEPTION.password);
  await page.getByRole('link', { name: 'مدیریت ارتباط با مشتری' }).click();
  await page.getByRole('button', { name: 'سرنخ جدید' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('نام', { exact: true }).fill(name);
  await dialog.getByLabel('موبایل', { exact: true }).fill(phone);
  await dialog.getByRole('button', { name: 'ذخیره' }).click();
  await expect(page.getByText('سرنخ ذخیره شد')).toBeVisible();

  await page.getByText(name).click();
  const detail = page.getByRole('dialog');
  await detail.getByPlaceholder('شرح فعالیت').fill('تماس اولیه، علاقه‌مند به پلن ماهانه');
  await detail.getByRole('button', { name: 'ثبت', exact: true }).click();
  await expect(detail.getByText('تماس اولیه، علاقه‌مند به پلن ماهانه')).toBeVisible();
  await detail.getByRole('button', { name: 'تبدیل به عضو' }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  await expect(page).toHaveURL(/\/members\/\d+/);
});

test('churn radar drafts a retention message and segments list members', async ({ page }) => {
  await login(page, RECEPTION.phone, RECEPTION.password);
  await page.getByRole('link', { name: 'رادار ریزش (AI)' }).click();
  await expect(page.getByRole('heading', { name: 'رادار ریزش اعضا' })).toBeVisible();
  await page.getByRole('button', { name: 'همه' }).click();
  await page.getByRole('button', { name: 'پیام نگهداشت' }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('textbox')).not.toHaveValue('');
  await dialog.getByRole('button', { name: 'انصراف' }).click();

  await page.getByRole('link', { name: 'مدیریت ارتباط با مشتری' }).click();
  await page.getByRole('tab', { name: 'بخش‌بندی و کمپین' }).click();
  await expect(page.getByText(/^[۰-۹]+ عضو$/).first()).toBeVisible();
});
