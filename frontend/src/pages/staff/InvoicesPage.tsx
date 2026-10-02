import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Link, MenuItem, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField } from '@mui/material';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Invoice, Member, Page } from '../../api/types';
import { Empty, Loading, PageHeader, StatusChip } from '../../components/common';
import { MemberPicker } from '../../components/MemberPicker';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatMoney, latinDigits } from '../../utils/format';
import { invoiceStatus } from '../../utils/labels';

export default function InvoicesPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [pay, setPay] = useState<Invoice | null>(null);
  const [method, setMethod] = useState('POS');
  const [create, setCreate] = useState(false);
  const [member, setMember] = useState<Member | null>(null);
  const [form, setForm] = useState({ title: '', amount: '', discount: '0' });
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['invoices', status, page],
    queryFn: () => api.get<Page<Invoice>>('/invoices', { params: { status: status || undefined, page, size: 20 } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  const payMut = useMutation({
    mutationFn: () => api.post(`/invoices/${pay!.id}/pay`, { method }),
    onSuccess: () => { notify('پرداخت ثبت شد'); setPay(null); qc.invalidateQueries({ queryKey: ['invoices'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const cancel = useMutation({
    mutationFn: (id: number) => api.post(`/invoices/${id}/cancel`),
    onSuccess: () => { notify('فاکتور لغو شد'); qc.invalidateQueries({ queryKey: ['invoices'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const createMut = useMutation({
    mutationFn: () => api.post('/invoices', { memberId: member!.id, title: form.title, amount: Number(latinDigits(form.amount)), discount: Number(latinDigits(form.discount) || 0) }),
    onSuccess: () => { notify('فاکتور صادر شد'); setCreate(false); setForm({ title: '', amount: '', discount: '0' }); setMember(null); qc.invalidateQueries({ queryKey: ['invoices'] }); },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <>
      <PageHeader title="فاکتورها" actions={<Button variant="contained" onClick={() => { setError(null); setCreate(true); }}>صدور فاکتور متفرقه</Button>} />
      <Card>
        <Stack direction="row" sx={{ p: 2 }}>
          <TextField select label="وضعیت" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} sx={{ maxWidth: 220 }}>
            <MenuItem value="">همه</MenuItem>
            {Object.entries(invoiceStatus).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
          </TextField>
        </Stack>
        {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
          <>
            <TableContainer>
              <Table size="small">
                <TableHead><TableRow><TableCell>شماره</TableCell><TableCell>عضو</TableCell><TableCell>شرح</TableCell>
                  <TableCell>مبلغ نهایی</TableCell><TableCell>تاریخ</TableCell><TableCell>وضعیت</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {data.content.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>{faDigits(i.number)}</TableCell>
                      <TableCell><Link component={RouterLink} to={`/members/${i.memberId}`}>{i.memberName}</Link></TableCell>
                      <TableCell>{i.title}</TableCell><TableCell>{formatMoney(i.total)}</TableCell>
                      <TableCell>{formatDate(i.createdAt)}</TableCell>
                      <TableCell><StatusChip map={invoiceStatus} value={i.status} /></TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {i.status === 'UNPAID' && <>
                          <Button size="small" onClick={() => setPay(i)}>ثبت پرداخت</Button>
                          <Button size="small" color="error" onClick={() => cancel.mutate(i.id)}>لغو</Button>
                        </>}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={20} rowsPerPageOptions={[20]}
              onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from, to, count }) => `${faDigits(from)}–${faDigits(to)} از ${faDigits(count)}`} />
          </>
        )}
      </Card>
      <Dialog open={!!pay} onClose={() => setPay(null)} maxWidth="xs" fullWidth>
        <DialogTitle>ثبت پرداخت {formatMoney(pay?.total)}</DialogTitle>
        <DialogContent>
          <TextField select label="روش پرداخت" value={method} onChange={(e) => setMethod(e.target.value)} sx={{ mt: 1 }}>
            <MenuItem value="POS">کارتخوان</MenuItem><MenuItem value="CASH">نقدی</MenuItem>
          </TextField>
        </DialogContent>
        <DialogActions><Button onClick={() => setPay(null)}>انصراف</Button>
          <Button variant="contained" onClick={() => payMut.mutate()} disabled={payMut.isPending}>ثبت</Button></DialogActions>
      </Dialog>
      <Dialog open={create} onClose={() => setCreate(false)} maxWidth="sm" fullWidth>
        <DialogTitle>صدور فاکتور متفرقه</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <MemberPicker value={member} onChange={setMember} />
            <TextField label="شرح (مثلاً جلسه مربی خصوصی، فروش مکمل)" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <Stack direction="row" spacing={2}>
              <TextField label="مبلغ (تومان)" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} inputProps={{ dir: 'ltr' }} />
              <TextField label="تخفیف (تومان)" value={form.discount} onChange={(e) => setForm({ ...form, discount: e.target.value })} inputProps={{ dir: 'ltr' }} />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setCreate(false)}>انصراف</Button>
          <Button variant="contained" onClick={() => createMut.mutate()} disabled={!member || !form.title || !form.amount || createMut.isPending}>صدور</Button></DialogActions>
      </Dialog>
    </>
  );
}
