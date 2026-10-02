import { useState } from 'react';
import { Alert, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, Switch,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Plan } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, PageHeader } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatMoney, latinDigits } from '../../utils/format';

const blank = { name: '', description: '', durationDays: '30', sessionLimit: '', price: '', maxFreezeDays: '0', active: true };

export default function PlansPage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [edit, setEdit] = useState<Plan | null | undefined>(undefined);
  const [form, setForm] = useState(blank);
  const [error, setError] = useState<string | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['plans', 'all'],
    queryFn: () => api.get<Plan[]>('/plans', { params: { includeInactive: true } }).then((r) => r.data),
  });
  const open = (p: Plan | null) => {
    setError(null);
    setEdit(p);
    setForm(p ? { name: p.name, description: p.description ?? '', durationDays: String(p.durationDays),
      sessionLimit: p.sessionLimit ? String(p.sessionLimit) : '', price: String(p.price), maxFreezeDays: String(p.maxFreezeDays), active: p.active } : blank);
  };
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, durationDays: Number(latinDigits(form.durationDays)), price: Number(latinDigits(form.price)),
        sessionLimit: form.sessionLimit ? Number(latinDigits(form.sessionLimit)) : null, maxFreezeDays: Number(latinDigits(form.maxFreezeDays) || 0) };
      return edit ? api.put(`/plans/${edit.id}`, body) : api.post('/plans', body);
    },
    onSuccess: () => { notify('پلن ذخیره شد'); setEdit(undefined); qc.invalidateQueries({ queryKey: ['plans'] }); },
    onError: (e) => setError(errorMessage(e)),
  });
  const set = (k: keyof typeof blank) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <PageHeader title="پلن‌های عضویت" actions={hasRole('ADMIN') && <Button variant="contained" onClick={() => open(null)}>پلن جدید</Button>} />
      <Card>
        {isLoading ? <Loading /> : !data?.length ? <Empty /> : (
          <TableContainer>
            <Table>
              <TableHead><TableRow><TableCell>نام</TableCell><TableCell>مدت</TableCell><TableCell>جلسات</TableCell>
                <TableCell>قیمت</TableCell><TableCell>حداکثر فریز</TableCell><TableCell>وضعیت</TableCell>{hasRole('ADMIN') && <TableCell />}</TableRow></TableHead>
              <TableBody>
                {data.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>{p.name}</TableCell><TableCell>{faDigits(p.durationDays)} روز</TableCell>
                    <TableCell>{p.sessionLimit ? faDigits(p.sessionLimit) : 'نامحدود'}</TableCell>
                    <TableCell>{formatMoney(p.price)}</TableCell><TableCell>{faDigits(p.maxFreezeDays)} روز</TableCell>
                    <TableCell>{p.active ? <Chip size="small" color="success" label="فعال" /> : <Chip size="small" label="غیرفعال" />}</TableCell>
                    {hasRole('ADMIN') && <TableCell><Button size="small" onClick={() => open(p)}>ویرایش</Button></TableCell>}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <Dialog open={edit !== undefined} onClose={() => setEdit(undefined)} maxWidth="sm" fullWidth>
        <DialogTitle>{edit ? 'ویرایش پلن' : 'پلن جدید'}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={12}><TextField label="نام پلن" value={form.name} onChange={set('name')} /></Grid>
            <Grid size={{ xs: 6 }}><TextField label="مدت (روز)" value={form.durationDays} onChange={set('durationDays')} inputProps={{ dir: 'ltr' }} /></Grid>
            <Grid size={{ xs: 6 }}><TextField label="تعداد جلسات (خالی = نامحدود)" value={form.sessionLimit} onChange={set('sessionLimit')} inputProps={{ dir: 'ltr' }} /></Grid>
            <Grid size={{ xs: 6 }}><TextField label="قیمت (تومان)" value={form.price} onChange={set('price')} inputProps={{ dir: 'ltr' }} /></Grid>
            <Grid size={{ xs: 6 }}><TextField label="حداکثر روز فریز" value={form.maxFreezeDays} onChange={set('maxFreezeDays')} inputProps={{ dir: 'ltr' }} /></Grid>
            <Grid size={12}><TextField label="توضیحات" value={form.description} onChange={set('description')} multiline minRows={2} /></Grid>
            <Grid size={12}><FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="قابل فروش" /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions><Button onClick={() => setEdit(undefined)}>انصراف</Button>
          <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>ذخیره</Button></DialogActions>
      </Dialog>
    </>
  );
}
