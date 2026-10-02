import { useEffect, useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, MenuItem, Stack, TextField,
  Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Plan, PurchaseResult, Quote } from '../../api/types';
import { JalaliDateField } from '../../components/JalaliDateField';
import { useNotify } from '../../components/Notify';
import { faDigits, formatMoney } from '../../utils/format';

export function SellMembershipDialog({ open, onClose, memberId, onDone }: {
  open: boolean; onClose: () => void; memberId: number; onDone: () => void;
}) {
  const notify = useNotify();
  const [planId, setPlanId] = useState<number | ''>('');
  const [start, setStart] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [applied, setApplied] = useState('');
  const [method, setMethod] = useState<'CASH' | 'POS' | 'LATER'>('POS');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) { setPlanId(''); setStart(null); setCode(''); setApplied(''); setError(null); setMethod('POS'); }
  }, [open]);

  const { data: plans } = useQuery({ queryKey: ['plans'], queryFn: () => api.get<Plan[]>('/plans').then((r) => r.data), enabled: open });
  const quote = useQuery({
    queryKey: ['quote', memberId, planId, applied],
    queryFn: () => api.get<Quote>('/memberships/quote', { params: { memberId, planId, discountCode: applied || undefined } }).then((r) => r.data),
    enabled: open && planId !== '',
    retry: false,
  });
  const sell = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<PurchaseResult>('/memberships', { memberId, planId, startDate: start, discountCode: applied || null });
      if (!data.paid && method !== 'LATER') await api.post(`/invoices/${data.invoiceId}/pay`, { method });
      return data;
    },
    onSuccess: () => {
      notify(method === 'LATER' ? 'اشتراک ثبت شد و فاکتور در انتظار پرداخت است' : 'اشتراک فروخته و فعال شد');
      onDone();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>فروش اشتراک</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField select label="پلن" value={planId} onChange={(e) => setPlanId(Number(e.target.value))}>
            {plans?.map((p) => <MenuItem key={p.id} value={p.id}>{p.name} — {formatMoney(p.price)}</MenuItem>)}
          </TextField>
          <JalaliDateField label="تاریخ شروع (پیش‌فرض: امروز یا پس از اشتراک فعلی)" value={start} onChange={setStart} disablePast />
          <Stack direction="row" spacing={1}>
            <TextField label="کد تخفیف جایزه" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} inputProps={{ dir: 'ltr' }} />
            <Button variant="outlined" onClick={() => setApplied(code.trim())}>اعمال</Button>
          </Stack>
          {quote.isError && <Alert severity="warning">{errorMessage(quote.error)}</Alert>}
          {quote.data && (
            <Stack spacing={0.5}>
              <Typography variant="body2">قیمت: {formatMoney(quote.data.price)}</Typography>
              {quote.data.tierDiscount > 0 && <Typography variant="body2">تخفیف سطح ({faDigits(quote.data.tierDiscountPercent)}٪): {formatMoney(quote.data.tierDiscount)}</Typography>}
              {quote.data.codeDiscount > 0 && <Typography variant="body2">تخفیف کد: {formatMoney(quote.data.codeDiscount)}</Typography>}
              <Divider />
              <Typography fontWeight={800}>قابل پرداخت: {formatMoney(quote.data.total)}</Typography>
            </Stack>
          )}
          <TextField select label="روش پرداخت" value={method} onChange={(e) => setMethod(e.target.value as typeof method)}>
            <MenuItem value="POS">کارتخوان</MenuItem>
            <MenuItem value="CASH">نقدی</MenuItem>
            <MenuItem value="LATER">بعداً (پرداخت آنلاین توسط عضو)</MenuItem>
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>انصراف</Button>
        <Button variant="contained" onClick={() => sell.mutate()} disabled={planId === '' || sell.isPending || quote.isError}>ثبت</Button>
      </DialogActions>
    </Dialog>
  );
}
