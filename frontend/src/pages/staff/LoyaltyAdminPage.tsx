import { useState } from 'react';
import { Alert, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel,
  Grid, List, ListItem, ListItemText, MenuItem, Stack, Switch, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Reward } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, PageHeader, StatCard } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatNumber, latinDigits } from '../../utils/format';
import { rewardType } from '../../utils/labels';
import { rewardValue } from '../member/MyLoyaltyPage';

const blank = { title: '', description: '', pointsCost: '', type: 'DISCOUNT_PERCENT', value: '', active: true };

export default function LoyaltyAdminPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [edit, setEdit] = useState<Reward | null | undefined>(undefined);
  const [form, setForm] = useState(blank);
  const [error, setError] = useState<string | null>(null);
  const [giftCode, setGiftCode] = useState('');
  const { data: rewards } = useQuery({ queryKey: ['rewards', 'all'], queryFn: () => api.get<Reward[]>('/loyalty/rewards', { params: { all: true } }).then((r) => r.data) });
  const { data: stats } = useQuery({ queryKey: ['loyalty-stats'], queryFn: () => api.get<{ issued: number; redeemed: number }>('/loyalty/stats').then((r) => r.data) });
  const { data: leaders } = useQuery({ queryKey: ['leaderboard'], queryFn: () => api.get<{ memberId: number; fullName: string; points: number }[]>('/loyalty/leaderboard').then((r) => r.data) });

  const open = (r: Reward | null) => {
    setError(null);
    setEdit(r);
    setForm(r ? { title: r.title, description: r.description ?? '', pointsCost: String(r.pointsCost), type: r.type, value: String(r.value), active: r.active } : blank);
  };
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, pointsCost: Number(latinDigits(form.pointsCost)), value: Number(latinDigits(form.value) || 0) };
      return edit ? api.put(`/loyalty/rewards/${edit.id}`, body) : api.post('/loyalty/rewards', body);
    },
    onSuccess: () => { notify('جایزه ذخیره شد'); setEdit(undefined); qc.invalidateQueries({ queryKey: ['rewards'] }); },
    onError: (e) => setError(errorMessage(e)),
  });
  const deliver = useMutation({
    mutationFn: () => api.post(`/loyalty/gifts/${encodeURIComponent(giftCode.trim())}/deliver`),
    onSuccess: () => { notify('هدیه تحویل داده شد'); setGiftCode(''); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });

  return (
    <>
      <PageHeader title="باشگاه مشتریان" subtitle="قواعد امتیازدهی و سطوح در بخش تنظیمات قابل تغییر است"
        actions={hasRole('ADMIN') && <Button variant="contained" onClick={() => open(null)}>جایزه جدید</Button>} />
      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="امتیاز صادرشده" value={formatNumber(stats?.issued)} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="امتیاز مصرف‌شده" value={formatNumber(stats?.redeemed)} /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}><CardContent>
            <Typography fontWeight={700} gutterBottom>تحویل هدیه</Typography>
            <Stack direction="row" spacing={1}>
              <TextField placeholder="کد هدیه عضو" value={giftCode} onChange={(e) => setGiftCode(e.target.value.toUpperCase())} inputProps={{ dir: 'ltr' }} />
              <Button variant="outlined" onClick={() => deliver.mutate()} disabled={!giftCode.trim()}>تحویل</Button>
            </Stack>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent><Typography fontWeight={700}>جوایز</Typography></CardContent>
            {!rewards?.length ? <Empty /> : (
              <Table size="small">
                <TableHead><TableRow><TableCell>عنوان</TableCell><TableCell>نوع</TableCell><TableCell>ارزش</TableCell><TableCell>امتیاز لازم</TableCell><TableCell>وضعیت</TableCell>{hasRole('ADMIN') && <TableCell />}</TableRow></TableHead>
                <TableBody>{rewards.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell>{r.title}</TableCell><TableCell>{rewardType[r.type]}</TableCell><TableCell>{rewardValue(r)}</TableCell>
                    <TableCell>{formatNumber(r.pointsCost)}</TableCell>
                    <TableCell>{r.active ? <Chip size="small" color="success" label="فعال" /> : <Chip size="small" label="غیرفعال" />}</TableCell>
                    {hasRole('ADMIN') && <TableCell><Button size="small" onClick={() => open(r)}>ویرایش</Button></TableCell>}
                  </TableRow>
                ))}</TableBody>
              </Table>
            )}
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card><CardContent>
            <Typography fontWeight={700}>برترین‌های ۳۰ روز اخیر</Typography>
            {!leaders?.length ? <Empty /> : (
              <List dense>{leaders.map((l, i) => (
                <ListItem key={l.memberId} divider secondaryAction={<Typography fontWeight={700}>{formatNumber(l.points)}</Typography>}>
                  <ListItemText primary={`${faDigits(i + 1)}. ${l.fullName}`} />
                </ListItem>
              ))}</List>
            )}
          </CardContent></Card>
        </Grid>
      </Grid>
      <Dialog open={edit !== undefined} onClose={() => setEdit(undefined)} maxWidth="sm" fullWidth>
        <DialogTitle>{edit ? 'ویرایش جایزه' : 'جایزه جدید'}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="عنوان" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <TextField label="توضیح" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <Stack direction="row" spacing={2}>
              <TextField select label="نوع" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                {Object.entries(rewardType).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
              </TextField>
              <TextField label={form.type === 'DISCOUNT_PERCENT' ? 'درصد' : form.type === 'DISCOUNT_AMOUNT' ? 'مبلغ (تومان)' : 'ارزش'} value={form.value}
                onChange={(e) => setForm({ ...form, value: e.target.value })} disabled={form.type === 'GIFT'} inputProps={{ dir: 'ltr' }} />
            </Stack>
            <TextField label="امتیاز لازم" value={form.pointsCost} onChange={(e) => setForm({ ...form, pointsCost: e.target.value })} inputProps={{ dir: 'ltr' }} />
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="فعال" />
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setEdit(undefined)}>انصراف</Button>
          <Button variant="contained" onClick={() => save.mutate()} disabled={!form.title || !form.pointsCost || save.isPending}>ذخیره</Button></DialogActions>
      </Dialog>
    </>
  );
}
