import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { Button, Stack, Typography } from '@mui/material';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import ErrorOutline from '@mui/icons-material/ErrorOutline';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Payment } from '../../api/types';
import { useAuth, homeFor } from '../../auth/AuthContext';
import { faDigits, formatMoney } from '../../utils/format';
import { AuthShell } from './AuthShell';

export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const { user } = useAuth();
  const ok = params.get('status') === 'PAID';
  const paymentId = params.get('paymentId');
  const { data } = useQuery({
    queryKey: ['payment', paymentId],
    queryFn: () => api.get<Payment>(`/payments/${paymentId}`).then((r) => r.data),
    enabled: !!paymentId && !!user,
  });
  return (
    <AuthShell title="نتیجه پرداخت">
      <Stack alignItems="center" spacing={2} textAlign="center">
        {ok ? <CheckCircleOutline color="success" sx={{ fontSize: 72 }} /> : <ErrorOutline color="error" sx={{ fontSize: 72 }} />}
        <Typography variant="h6">{ok ? 'پرداخت با موفقیت انجام شد' : 'پرداخت ناموفق بود'}</Typography>
        {data && (
          <Stack spacing={0.5}>
            <Typography>مبلغ: {formatMoney(data.amount)}</Typography>
            {data.refId && <Typography>کد پیگیری: {faDigits(data.refId)}</Typography>}
            <Typography variant="body2" color="text.secondary">{data.invoiceTitle}</Typography>
          </Stack>
        )}
        {!ok && <Typography color="text.secondary" variant="body2">در صورت کسر وجه، مبلغ حداکثر تا ۷۲ ساعت به حساب شما بازمی‌گردد.</Typography>}
        <Button variant="contained" component={RouterLink} to={user ? (user.role === 'MEMBER' ? '/me' : homeFor(user.role)) : '/login'}>
          بازگشت به پنل
        </Button>
      </Stack>
    </AuthShell>
  );
}
