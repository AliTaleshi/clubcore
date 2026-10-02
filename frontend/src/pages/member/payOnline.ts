import { api } from '../../api/client';

/** Starts an online payment and sends the browser to the gateway page. */
export async function payOnline(invoiceId: number): Promise<void> {
  const { data } = await api.post<{ redirectUrl: string }>('/payments/online', { invoiceId });
  window.location.assign(data.redirectUrl);
}
