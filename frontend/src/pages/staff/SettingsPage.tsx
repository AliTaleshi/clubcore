import { useEffect, useState } from 'react';
import { Alert, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel,
  Grid, MenuItem, Radio, RadioGroup, Stack, Switch, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs,
  TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { GatewayInfo, User } from '../../api/types';
import { PageHeader } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, isValidPhone, normalizePhone } from '../../utils/format';
import { roleLabels, type Role } from '../../utils/labels';

const LOYALTY_FIELDS: [string, string][] = [
  ['loyalty.checkinPoints', 'امتیاز هر حضور'], ['loyalty.tomanPerPoint', 'تومان به ازای هر امتیاز خرید'],
  ['loyalty.referralPoints', 'امتیاز معرفی دوست'], ['loyalty.silverThreshold', 'آستانه سطح نقره‌ای'],
  ['loyalty.goldThreshold', 'آستانه سطح طلایی'], ['loyalty.silverDiscountPercent', 'درصد تخفیف نقره‌ای'],
  ['loyalty.goldDiscountPercent', 'درصد تخفیف طلایی'],
];

export default function SettingsPage() {
  const [tab, setTab] = useState('gym');
  return (
    <>
      <PageHeader title="تنظیمات" />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="gym" label="باشگاه و امتیازها" />
        <Tab value="payment" label="درگاه پرداخت" />
        <Tab value="staff" label="کارکنان" />
      </Tabs>
      {tab === 'gym' && <GeneralSettings />}
      {tab === 'payment' && <PaymentSettings />}
      {tab === 'staff' && <StaffSettings />}
    </>
  );
}

function GeneralSettings() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Record<string, string>>('/settings').then((r) => r.data) });
  const [values, setValues] = useState<Record<string, string>>({});
  useEffect(() => { if (data) setValues(data); }, [data]);
  const save = useMutation({
    mutationFn: () => {
      const editable = ['gym.name', 'gym.phone', 'gym.address', ...LOYALTY_FIELDS.map(([k]) => k)];
      return api.put('/settings', Object.fromEntries(editable.map((k) => [k, values[k] ?? ''])));
    },
    onSuccess: () => { notify('تنظیمات ذخیره شد'); qc.invalidateQueries({ queryKey: ['settings'] }); qc.invalidateQueries({ queryKey: ['gym-info'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const field = (k: string, label: string, ltr = false) => (
    <TextField label={label} value={values[k] ?? ''} onChange={(e) => setValues({ ...values, [k]: e.target.value })} inputProps={ltr ? { dir: 'ltr' } : undefined} />
  );
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card><CardContent><Stack spacing={2}>
          <Typography fontWeight={700}>اطلاعات باشگاه</Typography>
          {field('gym.name', 'نام باشگاه')}{field('gym.phone', 'تلفن', true)}{field('gym.address', 'آدرس')}
        </Stack></CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card><CardContent><Stack spacing={2}>
          <Typography fontWeight={700}>قواعد باشگاه مشتریان</Typography>
          {LOYALTY_FIELDS.map(([k, l]) => <div key={k}>{field(k, l, true)}</div>)}
        </Stack></CardContent></Card>
      </Grid>
      <Grid size={12}><Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>ذخیره تنظیمات</Button></Grid>
    </Grid>
  );
}

function PaymentSettings() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data } = useQuery({ queryKey: ['gateways'], queryFn: () => api.get<GatewayInfo[]>('/payments/gateways').then((r) => r.data) });
  const activate = useMutation({
    mutationFn: (gateway: string) => api.put('/payments/gateways/active', { gateway }),
    onSuccess: () => { notify('درگاه فعال تغییر کرد'); qc.invalidateQueries({ queryKey: ['gateways'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const active = data?.find((g) => g.active)?.type ?? '';
  return (
    <Card sx={{ maxWidth: 560 }}><CardContent>
      <Typography fontWeight={700} gutterBottom>درگاه پرداخت اینترنتی فعال</Typography>
      <RadioGroup value={active} onChange={(e) => activate.mutate(e.target.value)}>
        {data?.map((g) => <FormControlLabel key={g.type} value={g.type} control={<Radio />} label={<>{g.title} {g.type === 'MOCK' && <Chip size="small" label="آزمایشی" sx={{ mr: 1 }} />}</>} />)}
      </RadioGroup>
      <Alert severity="info" sx={{ mt: 2 }}>
        کد پذیرنده زرین‌پال و زیبال از طریق متغیرهای محیطی (ZARINPAL_MERCHANT_ID، ZIBAL_MERCHANT) تنظیم می‌شود.
        حالت sandbox زرین‌پال و پذیرنده «zibal» برای آزمایش هستند.
      </Alert>
    </CardContent></Card>
  );
}

function StaffSettings() {
  const qc = useQueryClient();
  const notify = useNotify();
  const [edit, setEdit] = useState<User | null | undefined>(undefined);
  const [form, setForm] = useState({ fullName: '', phone: '', role: 'RECEPTIONIST' as Role, password: '', active: true });
  const [error, setError] = useState<string | null>(null);
  const { data } = useQuery({ queryKey: ['staff'], queryFn: () => api.get<User[]>('/users').then((r) => r.data) });
  const open = (u: User | null) => {
    setError(null);
    setEdit(u);
    setForm(u ? { fullName: u.fullName, phone: u.phone, role: u.role, password: '', active: u.active } : { fullName: '', phone: '', role: 'RECEPTIONIST', password: '', active: true });
  };
  const save = useMutation({
    mutationFn: () => {
      if (!isValidPhone(form.phone)) throw new Error('شماره موبایل نامعتبر است');
      const body = { ...form, phone: normalizePhone(form.phone), password: form.password || null };
      return edit ? api.put(`/users/${edit.id}`, body) : api.post('/users', body);
    },
    onSuccess: () => { notify('ذخیره شد'); setEdit(undefined); qc.invalidateQueries({ queryKey: ['staff'] }); },
    onError: (e) => setError(e instanceof Error && !('isAxiosError' in e) ? e.message : errorMessage(e)),
  });
  return (
    <Card>
      <CardContent><Button variant="contained" onClick={() => open(null)}>کارمند جدید</Button></CardContent>
      <Table>
        <TableHead><TableRow><TableCell>نام</TableCell><TableCell>موبایل</TableCell><TableCell>نقش</TableCell><TableCell>وضعیت</TableCell><TableCell /></TableRow></TableHead>
        <TableBody>{data?.map((u) => (
          <TableRow key={u.id}>
            <TableCell>{u.fullName}</TableCell><TableCell>{faDigits(u.phone)}</TableCell><TableCell>{roleLabels[u.role]}</TableCell>
            <TableCell>{u.active ? <Chip size="small" color="success" label="فعال" /> : <Chip size="small" label="غیرفعال" />}</TableCell>
            <TableCell><Button size="small" onClick={() => open(u)}>ویرایش</Button></TableCell>
          </TableRow>
        ))}</TableBody>
      </Table>
      <Dialog open={edit !== undefined} onClose={() => setEdit(undefined)} maxWidth="xs" fullWidth>
        <DialogTitle>{edit ? 'ویرایش کارمند' : 'کارمند جدید'}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="نام" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            <TextField label="موبایل" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputProps={{ dir: 'ltr' }} />
            <TextField select label="نقش" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
              {(['ADMIN', 'RECEPTIONIST', 'ACCOUNTANT', 'COACH'] as Role[]).map((r) => <MenuItem key={r} value={r}>{roleLabels[r]}</MenuItem>)}
            </TextField>
            <TextField label={edit ? 'رمز عبور جدید (اختیاری)' : 'رمز عبور'} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            {edit && <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="فعال" />}
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setEdit(undefined)}>انصراف</Button>
          <Button variant="contained" onClick={() => save.mutate()} disabled={!form.fullName || !form.phone || (!edit && form.password.length < 8) || save.isPending}>ذخیره</Button></DialogActions>
      </Dialog>
    </Card>
  );
}
