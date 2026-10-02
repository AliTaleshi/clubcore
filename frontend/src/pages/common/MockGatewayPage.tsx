import { useSearchParams } from 'react-router-dom';
import { Alert, Button, Stack, Typography } from '@mui/material';
import { formatMoney } from '../../utils/format';
import { AuthShell } from './AuthShell';

/** Simulated bank page used by the MOCK gateway in development and automated tests. */
export default function MockGatewayPage() {
  const [params] = useSearchParams();
  const authority = params.get('authority') ?? '';
  const callback = params.get('callback') ?? '';
  const amount = Number(params.get('amount') ?? 0);
  // Only follow callbacks on our own origin.
  const safe = callback.startsWith(window.location.origin + '/api/payments/callback/') || callback.startsWith('/api/payments/callback/');
  const go = (status: 'OK' | 'NOK') => {
    const url = new URL(callback, window.location.origin);
    url.searchParams.set('authority', authority);
    url.searchParams.set('status', status);
    window.location.assign(url.toString());
  };
  return (
    <AuthShell title="درگاه پرداخت آزمایشی">
      <Stack spacing={2}>
        <Alert severity="warning">این صفحه فقط برای محیط توسعه و آزمایش است و هیچ تراکنش واقعی انجام نمی‌شود.</Alert>
        <Typography>{params.get('description')}</Typography>
        <Typography variant="h5" textAlign="center">{formatMoney(amount)}</Typography>
        {!safe && <Alert severity="error">آدرس بازگشت نامعتبر است</Alert>}
        <Button variant="contained" color="success" size="large" disabled={!safe} onClick={() => go('OK')}>پرداخت موفق</Button>
        <Button variant="outlined" color="error" disabled={!safe} onClick={() => go('NOK')}>انصراف از پرداخت</Button>
      </Stack>
    </AuthShell>
  );
}
