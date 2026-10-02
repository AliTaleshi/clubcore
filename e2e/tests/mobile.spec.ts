import { expect, test } from '@playwright/test';
import { ADMIN, login } from './helpers';

test('layout works at phone width with a collapsible drawer and no horizontal scroll', async ({ page }) => {
  await login(page, ADMIN.phone, ADMIN.password);
  await expect(page.getByRole('heading', { name: 'داشبورد' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await page.getByRole('button', { name: 'منو' }).click();
  await page.getByRole('link', { name: 'اعضا' }).click();
  await expect(page.getByRole('heading', { name: 'اعضا' })).toBeVisible();
});
