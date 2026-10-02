import { expect, test } from '@playwright/test';
import { ADMIN, login, uniquePhone } from './helpers';

test('admin creates a plan and member, sells a membership, checks the member in and sees revenue', async ({ page }) => {
  const planName = `پلن آزمون ${Date.now() % 100000}`;
  const phone = uniquePhone();
  const memberName = `عضو آزمون ${phone.slice(-4)}`;

  await login(page, ADMIN.phone, ADMIN.password);
  await expect(page.getByRole('heading', { name: 'داشبورد' })).toBeVisible();
  await expect(page.getByText('اعضای فعال')).toBeVisible();

  // Plan
  await page.getByRole('link', { name: 'پلن‌های عضویت' }).click();
  await page.getByRole('button', { name: 'پلن جدید' }).click();
  const planDialog = page.getByRole('dialog');
  await planDialog.getByLabel('نام پلن').fill(planName);
  await planDialog.getByLabel('مدت (روز)').fill('30');
  await planDialog.getByLabel('قیمت (تومان)').fill('900000');
  await planDialog.getByRole('button', { name: 'ذخیره' }).click();
  await expect(page.getByRole('cell', { name: planName })).toBeVisible();

  // Member
  await page.getByRole('link', { name: 'اعضا' }).click();
  await page.getByRole('button', { name: 'عضو جدید' }).click();
  const memberDialog = page.getByRole('dialog');
  await memberDialog.getByLabel(/نام و نام خانوادگی/).fill(memberName);
  await memberDialog.getByLabel(/شماره موبایل/).fill(phone);
  await memberDialog.getByLabel(/رمز عبور پنل/).fill('Member@123');
  await memberDialog.getByRole('button', { name: 'ذخیره' }).click();
  await expect(page.getByRole('heading', { name: memberName })).toBeVisible();
  await expect(page.getByText('این عضو اشتراکی ندارد')).toBeVisible();

  // Sell membership, paid by POS
  await page.getByRole('tab', { name: 'اشتراک‌ها' }).click();
  await page.getByRole('button', { name: 'فروش اشتراک' }).click();
  const sell = page.getByRole('dialog');
  await sell.getByRole('combobox', { name: 'پلن' }).click();
  await page.getByRole('option', { name: new RegExp(planName) }).click();
  await expect(sell.getByText('قابل پرداخت: ۹۰۰٬۰۰۰ تومان')).toBeVisible();
  await sell.getByRole('button', { name: 'ثبت' }).click();
  await expect(page.getByText('اشتراک فروخته و فعال شد')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'فعال', exact: true })).toBeVisible();

  // Kiosk: manual entry by phone toggles check-in then check-out
  await page.getByRole('link', { name: 'کیوسک ورود و خروج' }).click();
  await page.getByRole('tab', { name: 'دستی' }).click();
  await page.getByLabel('شناسه عضو').fill(phone);
  await page.getByLabel('شناسه عضو').press('Enter');
  await expect(page.getByText('خوش آمدید')).toBeVisible();
  await expect(page.getByText(memberName).first()).toBeVisible();
  await page.getByLabel('شناسه عضو').fill(phone);
  await page.getByLabel('شناسه عضو').press('Enter');
  await expect(page.getByText('خدا نگهدار')).toBeVisible();

  // Accounting shows membership revenue posted automatically
  await page.getByRole('link', { name: 'حسابداری' }).click();
  await expect(page.getByText('صورت سود و زیان')).toBeVisible();
  await expect(page.getByText('درآمد شهریه عضویت')).toBeVisible();
  await page.getByRole('tab', { name: 'دفتر روزنامه' }).click();
  await expect(page.getByText(new RegExp(`دریافت وجه فاکتور .* - ${memberName}`))).toBeVisible();
});
