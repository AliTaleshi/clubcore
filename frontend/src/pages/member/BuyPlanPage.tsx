import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, CardActions, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Divider,
  Grid, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Plan, PurchaseResult, Quote } from '../../api/types';
import { Loading, PageHeader } from '../../components/common';
import { JalaliDateField } from '../../components/JalaliDateField';
import { faDigits, formatMoney } from '../../utils/format';
import { payOnline } from './payOnline';

export default function BuyPlanPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [plan, setPlan] = useState<Plan | null>(null);
  const [code, setCode] = useState('');
  const [appliedCode, setAppliedCode] = useState('');
  const [start, setStart] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: plans, isLoading } = useQuery({
    queryKey: ['public-plans'],
    queryFn: () => api.get<Plan[]>('/public/plans').then((r) => r.data),
  });
  const quote = useQuery({
    queryKey: ['my-quote', plan?.id, appliedCode],
    queryFn: () => api.get<Quote>('/me/memberships/quote', { params: { planId: plan!.id, discountCode: appliedCode || undefined } })
      .then((r) => r.data),
    enabled: !!plan,
    retry: false,
  });
  const buy = useMutation({
    mutationFn: () => api.post<PurchaseResult>('/me/memberships', {
      planId: plan!.id, startDate: start, discountCode: appliedCode || null,
    }).then((r) => r.data),
    onSuccess: async (res) => {
      qc.invalidateQueries({ queryKey: ['me-member'] });
      qc.invalidateQueries({ queryKey: ['my-invoices'] });
      if (res.paid) {
        navigate('/me');
        return;
      }
      try {
        await payOnline(res.invoiceId);
      } catch (e) {
        setError(errorMessage(e) + ' — می‌توانید از بخش فاکتورها دوباره پرداخت کنید.');
      }
    },
    onError: (e) => setError(errorMessage(e)),
  });

  if (isLoading) return <Loading />;
  return (
    <>
      <PageHeader title="خرید یا تمدید اشتراک" subtitle="پلن مورد نظر را انتخاب کنید؛ پرداخت به‌صورت اینترنتی انجام می‌شود" />
      <Grid container spacing={2}>
        {plans?.map((p) => (
          <Grid key={p.id} size={{ xs: 12, sm: 6, lg: 3 }}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flex: 1 }}>
                <Typography variant="h6">{p.name}</Typography>
                <Typography variant="h5" color="primary" sx={{ my: 1.5, fontWeight: 800 }}>{formatMoney(p.price)}</Typography>
                <Stack spacing={0.5}>
                  <Typography variant="body2">مدت: {faDigits(p.durationDays)} روز</Typography>
                  <Typography variant="body2">{p.sessionLimit ? `${faDigits(p.sessionLimit)} جلسه` : 'ورود نامحدود'}</Typography>
                  {p.maxFreezeDays > 0 && <Typography variant="body2">امکان فریز تا {faDigits(p.maxFreezeDays)} روز</Typography>}
                  {p.description && <Typography variant="body2" color="text.secondary">{p.description}</Typography>}
                </Stack>
              </CardContent>
              <CardActions>
                <Button fullWidth variant="contained" onClick={() => { setPlan(p); setError(null); setCode(''); setAppliedCode(''); setStart(null); }}>
                  انتخاب
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={!!plan} onClose={() => setPlan(null)} maxWidth="xs" fullWidth>
        <DialogTitle>خرید «{plan?.name}»</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && (
              <Alert severity="error" action={error.includes('در انتظار پرداخت') && (
                <Button color="inherit" size="small" component={RouterLink} to="/me/invoices">فاکتورها</Button>
              )}>{error}</Alert>
            )}
            <JalaliDateField label="تاریخ شروع (اختیاری)" value={start} onChange={setStart} disablePast />
            <Stack direction="row" spacing={1}>
              <TextField label="کد تخفیف جایزه" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} inputProps={{ dir: 'ltr' }} />
              <Button variant="outlined" onClick={() => { setError(null); setAppliedCode(code.trim()); }}>اعمال</Button>
            </Stack>
            {quote.isError && <Alert severity="warning">{errorMessage(quote.error)}</Alert>}
            {quote.data && (
              <Stack spacing={0.5}>
                <Row label="قیمت پلن" value={formatMoney(quote.data.price)} />
                {quote.data.tierDiscount > 0 && <Row label={`تخفیف سطح (${faDigits(quote.data.tierDiscountPercent)}٪)`} value={`− ${formatMoney(quote.data.tierDiscount)}`} />}
                {quote.data.codeDiscount > 0 && <Row label="تخفیف کد جایزه" value={`− ${formatMoney(quote.data.codeDiscount)}`} />}
                <Divider />
                <Row label="مبلغ قابل پرداخت" value={formatMoney(quote.data.total)} bold />
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPlan(null)}>انصراف</Button>
          <Button variant="contained" onClick={() => buy.mutate()} disabled={buy.isPending || quote.isError || !quote.data}>
            پرداخت و فعال‌سازی
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography fontWeight={bold ? 800 : 400}>{label}</Typography>
      <Typography fontWeight={bold ? 800 : 400}>{value}</Typography>
    </Stack>
  );
}
