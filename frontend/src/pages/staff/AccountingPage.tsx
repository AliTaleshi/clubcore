import { Fragment, useMemo, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, Collapse, Dialog, DialogActions, DialogContent, DialogTitle, Grid,
  IconButton, MenuItem, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  Tabs, TextField, Typography } from '@mui/material';
import KeyboardArrowDown from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUp from '@mui/icons-material/KeyboardArrowUp';
import AddCircleOutline from '@mui/icons-material/AddCircleOutline';
import RemoveCircleOutline from '@mui/icons-material/RemoveCircleOutline';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Account, Expense, IncomeStatement, JournalEntry, LedgerRow, Page, SeriesPoint, TrialRow } from '../../api/types';
import { ChartBox, Empty, Loading, PageHeader, StatCard } from '../../components/common';
import { JalaliDateField } from '../../components/JalaliDateField';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatMoney, jalaliMonthKey, latinDigits, toIsoDate } from '../../utils/format';
import { accountType } from '../../utils/labels';

const daysAgo = (n: number) => toIsoDate(new Date(Date.now() - n * 86400000));
const compact = (n: number) => (Math.abs(n) >= 1_000_000 ? `${faDigits(Math.round(n / 1_000_000))}م` : faDigits(n));

function useAccounts() {
  return useQuery({ queryKey: ['accounts'], queryFn: () => api.get<Account[]>('/accounting/accounts').then((r) => r.data) });
}

export default function AccountingPage() {
  const [tab, setTab] = useState('reports');
  const [from, setFrom] = useState<string | null>(daysAgo(89));
  const [to, setTo] = useState<string | null>(daysAgo(0));
  return (
    <>
      <PageHeader title="حسابداری" subtitle="ثبت دوطرفه اسناد؛ پرداخت‌ها و هزینه‌ها به‌صورت خودکار سند می‌خورند"
        actions={<Stack direction="row" spacing={1} sx={{ minWidth: { sm: 360 } }}>
          <JalaliDateField label="از تاریخ" value={from} onChange={setFrom} />
          <JalaliDateField label="تا تاریخ" value={to} onChange={setTo} />
        </Stack>} />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="reports" label="گزارش سود و زیان" />
        <Tab value="journal" label="دفتر روزنامه" />
        <Tab value="expenses" label="هزینه‌ها" />
        <Tab value="trial" label="تراز آزمایشی و حساب‌ها" />
      </Tabs>
      {from && to && tab === 'reports' && <Reports from={from} to={to} />}
      {from && to && tab === 'journal' && <Journal from={from} to={to} />}
      {from && to && tab === 'expenses' && <Expenses from={from} to={to} />}
      {from && to && tab === 'trial' && <TrialBalance from={from} to={to} />}
    </>
  );
}

function Reports({ from, to }: { from: string; to: string }) {
  const is = useQuery({ queryKey: ['income-statement', from, to],
    queryFn: () => api.get<IncomeStatement>('/accounting/reports/income-statement', { params: { from, to } }).then((r) => r.data) });
  const series = useQuery({ queryKey: ['series', from, to],
    queryFn: () => api.get<SeriesPoint[]>('/accounting/reports/series', { params: { from, to } }).then((r) => r.data) });
  const sales = useQuery({ queryKey: ['sales-by-plan', from, to],
    queryFn: () => api.get<{ plan: string; count: number; revenue: number }[]>('/accounting/reports/sales-by-plan', { params: { from, to } }).then((r) => r.data) });

  // Aggregate daily points into Jalali months for the chart.
  const monthly = useMemo(() => {
    const map = new Map<string, { month: string; income: number; expense: number }>();
    for (const p of series.data ?? []) {
      const key = jalaliMonthKey(p.date);
      const row = map.get(key) ?? { month: key, income: 0, expense: 0 };
      row.income += p.income;
      row.expense += p.expense;
      map.set(key, row);
    }
    return [...map.values()];
  }, [series.data]);

  if (is.isLoading || !is.data) return <Loading />;
  const d = is.data;
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, sm: 4 }}><StatCard label="جمع درآمد" value={formatMoney(d.totalIncome)} color="success.main" /></Grid>
      <Grid size={{ xs: 12, sm: 4 }}><StatCard label="جمع هزینه" value={formatMoney(d.totalExpense)} /></Grid>
      <Grid size={{ xs: 12, sm: 4 }}><StatCard label={d.netProfit >= 0 ? 'سود خالص' : 'زیان خالص'} value={formatMoney(Math.abs(d.netProfit))} /></Grid>
      <Grid size={{ xs: 12, lg: 7 }}>
        <Card><CardContent>
          <Typography fontWeight={700} gutterBottom>درآمد و هزینه به تفکیک ماه</Typography>
          <ChartBox height={300}>
            <ResponsiveContainer>
              <BarChart data={monthly}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                <XAxis dataKey="month" tickFormatter={(m) => faDigits(m)} fontSize={11} />
                <YAxis tickFormatter={compact} fontSize={11} width={45} />
                <Tooltip labelFormatter={(m) => faDigits(String(m))} formatter={(v, n) => [formatMoney(Number(v)), n === 'income' ? 'درآمد' : 'هزینه']} />
                <Legend formatter={(v) => (v === 'income' ? 'درآمد' : 'هزینه')} />
                <Bar dataKey="income" fill="#0f766e" radius={[4, 4, 0, 0]} />
                <Bar dataKey="expense" fill="#dc2626" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartBox>
        </CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 5 }}>
        <Card><CardContent>
          <Typography fontWeight={700} gutterBottom>صورت سود و زیان</Typography>
          <Table size="small">
            <TableBody>
              {d.income.filter((r) => r.balance).map((r) => <TableRow key={r.accountId}><TableCell>{r.name}</TableCell><TableCell align="left">{formatMoney(r.balance)}</TableCell></TableRow>)}
              <TableRow><TableCell sx={{ fontWeight: 800 }}>جمع درآمد</TableCell><TableCell align="left" sx={{ fontWeight: 800 }}>{formatMoney(d.totalIncome)}</TableCell></TableRow>
              {d.expenses.filter((r) => r.balance).map((r) => <TableRow key={r.accountId}><TableCell>{r.name}</TableCell><TableCell align="left">({formatMoney(r.balance)})</TableCell></TableRow>)}
              <TableRow><TableCell sx={{ fontWeight: 800 }}>جمع هزینه</TableCell><TableCell align="left" sx={{ fontWeight: 800 }}>({formatMoney(d.totalExpense)})</TableCell></TableRow>
              <TableRow><TableCell sx={{ fontWeight: 900 }}>{d.netProfit >= 0 ? 'سود خالص' : 'زیان خالص'}</TableCell>
                <TableCell align="left" sx={{ fontWeight: 900, color: d.netProfit >= 0 ? 'success.main' : 'error.main' }}>{formatMoney(Math.abs(d.netProfit))}</TableCell></TableRow>
            </TableBody>
          </Table>
        </CardContent></Card>
      </Grid>
      <Grid size={12}>
        <Card><CardContent>
          <Typography fontWeight={700} gutterBottom>فروش به تفکیک پلن</Typography>
          {!sales.data?.length ? <Empty /> : (
            <Table size="small">
              <TableHead><TableRow><TableCell>پلن</TableCell><TableCell>تعداد فروش</TableCell><TableCell>مبلغ</TableCell></TableRow></TableHead>
              <TableBody>{sales.data.map((s) => <TableRow key={s.plan}><TableCell>{s.plan}</TableCell><TableCell>{faDigits(s.count)}</TableCell><TableCell>{formatMoney(s.revenue)}</TableCell></TableRow>)}</TableBody>
            </Table>
          )}
        </CardContent></Card>
      </Grid>
    </Grid>
  );
}

function Journal({ from, to }: { from: string; to: string }) {
  const [page, setPage] = useState(0);
  const [openRow, setOpenRow] = useState<number | null>(null);
  const [create, setCreate] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['journal', from, to, page],
    queryFn: () => api.get<Page<JournalEntry>>('/accounting/journal', { params: { from, to, page, size: 20 } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  return (
    <Card>
      <Box sx={{ p: 2 }}><Button variant="contained" onClick={() => setCreate(true)}>سند دستی</Button></Box>
      {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
        <>
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow><TableCell /><TableCell>شماره سند</TableCell><TableCell>تاریخ</TableCell><TableCell>شرح</TableCell><TableCell>مبلغ</TableCell></TableRow></TableHead>
              <TableBody>
                {data.content.map((e) => (
                  <Fragment key={e.id}>
                    <TableRow hover>
                      <TableCell padding="checkbox"><IconButton size="small" onClick={() => setOpenRow(openRow === e.id ? null : e.id)} aria-label="جزئیات">
                        {openRow === e.id ? <KeyboardArrowUp /> : <KeyboardArrowDown />}</IconButton></TableCell>
                      <TableCell>{faDigits(e.id)}</TableCell><TableCell>{formatDate(e.entryDate)}</TableCell>
                      <TableCell>{e.description}</TableCell><TableCell>{formatMoney(e.total)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={5} sx={{ py: 0, border: 0 }}>
                        <Collapse in={openRow === e.id} unmountOnExit>
                          <Table size="small" sx={{ my: 1 }}>
                            <TableHead><TableRow><TableCell>حساب</TableCell><TableCell>بدهکار</TableCell><TableCell>بستانکار</TableCell></TableRow></TableHead>
                            <TableBody>{e.lines.map((l, i) => (
                              <TableRow key={i}><TableCell>{faDigits(l.accountCode)} - {l.accountName}</TableCell>
                                <TableCell>{l.debit ? formatMoney(l.debit) : ''}</TableCell><TableCell>{l.credit ? formatMoney(l.credit) : ''}</TableCell></TableRow>
                            ))}</TableBody>
                          </Table>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </Fragment>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={20} rowsPerPageOptions={[20]}
            onPageChange={(_, p) => setPage(p)} labelDisplayedRows={({ from: f, to: t, count }) => `${faDigits(f)}–${faDigits(t)} از ${faDigits(count)}`} />
        </>
      )}
      <ManualEntryDialog open={create} onClose={() => setCreate(false)} />
    </Card>
  );
}

function ManualEntryDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data: accounts } = useAccounts();
  const [description, setDescription] = useState('');
  const [date, setDate] = useState<string | null>(toIsoDate(new Date()));
  const [lines, setLines] = useState([{ accountId: '', debit: '', credit: '' }, { accountId: '', debit: '', credit: '' }]);
  const [error, setError] = useState<string | null>(null);
  const num = (s: string) => Number(latinDigits(s) || 0);
  const totalDebit = lines.reduce((s, l) => s + num(l.debit), 0);
  const totalCredit = lines.reduce((s, l) => s + num(l.credit), 0);
  const save = useMutation({
    mutationFn: () => api.post('/accounting/journal', { entryDate: date, description,
      lines: lines.map((l) => ({ accountId: Number(l.accountId), debit: num(l.debit), credit: num(l.credit) })) }),
    onSuccess: () => { notify('سند ثبت شد'); onClose(); qc.invalidateQueries({ queryKey: ['journal'] }); },
    onError: (e) => setError(errorMessage(e)),
  });
  const update = (i: number, k: 'accountId' | 'debit' | 'credit', v: string) =>
    setLines(lines.map((l, j) => (j === i ? { ...l, [k]: v } : l)));
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>ثبت سند دستی</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1, mb: 2 }}>
          <JalaliDateField label="تاریخ سند" value={date} onChange={setDate} />
          <TextField label="شرح سند" value={description} onChange={(e) => setDescription(e.target.value)} />
        </Stack>
        {lines.map((l, i) => (
          <Stack key={i} direction="row" spacing={1} sx={{ mb: 1 }} alignItems="center">
            <TextField select label="حساب" value={l.accountId} onChange={(e) => update(i, 'accountId', e.target.value)} sx={{ flex: 2 }}>
              {accounts?.map((a) => <MenuItem key={a.id} value={a.id}>{faDigits(a.code)} - {a.name}</MenuItem>)}
            </TextField>
            <TextField label="بدهکار" value={l.debit} onChange={(e) => update(i, 'debit', e.target.value)} sx={{ flex: 1 }} inputProps={{ dir: 'ltr' }} />
            <TextField label="بستانکار" value={l.credit} onChange={(e) => update(i, 'credit', e.target.value)} sx={{ flex: 1 }} inputProps={{ dir: 'ltr' }} />
            <IconButton onClick={() => setLines(lines.filter((_, j) => j !== i))} disabled={lines.length <= 2} aria-label="حذف ردیف"><RemoveCircleOutline /></IconButton>
          </Stack>
        ))}
        <Button startIcon={<AddCircleOutline />} onClick={() => setLines([...lines, { accountId: '', debit: '', credit: '' }])}>ردیف جدید</Button>
        <Alert severity={totalDebit === totalCredit && totalDebit > 0 ? 'success' : 'warning'} sx={{ mt: 2 }}>
          جمع بدهکار: {formatMoney(totalDebit)} — جمع بستانکار: {formatMoney(totalCredit)}
        </Alert>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>انصراف</Button>
        <Button variant="contained" onClick={() => save.mutate()} disabled={!description || totalDebit !== totalCredit || totalDebit === 0 || save.isPending}>ثبت سند</Button></DialogActions>
    </Dialog>
  );
}

function Expenses({ from, to }: { from: string; to: string }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data: accounts } = useAccounts();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ accountId: '', paidFromAccountId: '', amount: '', description: '', expenseDate: toIsoDate(new Date()) as string | null });
  const [error, setError] = useState<string | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['expenses', from, to],
    queryFn: () => api.get<Page<Expense>>('/accounting/expenses', { params: { from, to, size: 100 } }).then((r) => r.data),
  });
  const save = useMutation({
    mutationFn: () => api.post('/accounting/expenses', { ...form, accountId: Number(form.accountId), paidFromAccountId: Number(form.paidFromAccountId), amount: Number(latinDigits(form.amount)) }),
    onSuccess: () => { notify('هزینه ثبت شد'); setOpen(false); qc.invalidateQueries({ queryKey: ['expenses'] }); qc.invalidateQueries({ queryKey: ['income-statement'] }); },
    onError: (e) => setError(errorMessage(e)),
  });
  return (
    <Card>
      <Box sx={{ p: 2 }}><Button variant="contained" onClick={() => { setError(null); setOpen(true); }}>ثبت هزینه</Button></Box>
      {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
        <Table size="small">
          <TableHead><TableRow><TableCell>تاریخ</TableCell><TableCell>نوع هزینه</TableCell><TableCell>شرح</TableCell><TableCell>پرداخت از</TableCell><TableCell>مبلغ</TableCell></TableRow></TableHead>
          <TableBody>{data.content.map((x) => (
            <TableRow key={x.id}><TableCell>{formatDate(x.expenseDate)}</TableCell><TableCell>{x.accountName}</TableCell>
              <TableCell>{x.description}</TableCell><TableCell>{x.paidFromName}</TableCell><TableCell>{formatMoney(x.amount)}</TableCell></TableRow>
          ))}</TableBody>
        </Table>
      )}
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>ثبت هزینه</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField select label="نوع هزینه" value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}>
              {accounts?.filter((a) => a.type === 'EXPENSE').map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
            </TextField>
            <TextField select label="پرداخت از" value={form.paidFromAccountId} onChange={(e) => setForm({ ...form, paidFromAccountId: e.target.value })}>
              {accounts?.filter((a) => a.type === 'ASSET').map((a) => <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}
            </TextField>
            <TextField label="مبلغ (تومان)" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} inputProps={{ dir: 'ltr' }} />
            <JalaliDateField label="تاریخ" value={form.expenseDate} onChange={(v) => setForm({ ...form, expenseDate: v })} />
            <TextField label="شرح" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setOpen(false)}>انصراف</Button>
          <Button variant="contained" onClick={() => save.mutate()} disabled={!form.accountId || !form.paidFromAccountId || !form.amount || !form.description || save.isPending}>ثبت</Button></DialogActions>
      </Dialog>
    </Card>
  );
}

function TrialBalance({ from, to }: { from: string; to: string }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const [ledger, setLedger] = useState<TrialRow | null>(null);
  const [create, setCreate] = useState(false);
  const [acc, setAcc] = useState({ code: '', name: '', type: 'EXPENSE' });
  const { data, isLoading } = useQuery({
    queryKey: ['trial-balance', from, to],
    queryFn: () => api.get<TrialRow[]>('/accounting/reports/trial-balance', { params: { from, to } }).then((r) => r.data),
  });
  const ledgerQ = useQuery({
    queryKey: ['ledger', ledger?.accountId, from, to],
    queryFn: () => api.get<LedgerRow[]>(`/accounting/reports/ledger/${ledger!.accountId}`, { params: { from, to } }).then((r) => r.data),
    enabled: !!ledger,
  });
  const createAcc = useMutation({
    mutationFn: () => api.post('/accounting/accounts', { ...acc, code: latinDigits(acc.code) }),
    onSuccess: () => { notify('حساب ایجاد شد'); setCreate(false); qc.invalidateQueries({ queryKey: ['trial-balance'] }); qc.invalidateQueries({ queryKey: ['accounts'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  if (isLoading || !data) return <Loading />;
  const td = data.reduce((s, r) => s + r.debit, 0);
  const tc = data.reduce((s, r) => s + r.credit, 0);
  return (
    <Card>
      <Box sx={{ p: 2 }}><Button variant="outlined" onClick={() => setCreate(true)}>حساب جدید</Button></Box>
      <TableContainer>
        <Table size="small">
          <TableHead><TableRow><TableCell>کد</TableCell><TableCell>حساب</TableCell><TableCell>نوع</TableCell><TableCell>گردش بدهکار</TableCell><TableCell>گردش بستانکار</TableCell><TableCell>مانده</TableCell></TableRow></TableHead>
          <TableBody>
            {data.map((r) => (
              <TableRow key={r.accountId} hover sx={{ cursor: 'pointer' }} onClick={() => setLedger(r)}>
                <TableCell>{faDigits(r.code)}</TableCell><TableCell>{r.name}</TableCell><TableCell>{accountType[r.type]}</TableCell>
                <TableCell>{formatMoney(r.debit)}</TableCell><TableCell>{formatMoney(r.credit)}</TableCell><TableCell>{formatMoney(r.balance)}</TableCell>
              </TableRow>
            ))}
            <TableRow><TableCell colSpan={3} sx={{ fontWeight: 800 }}>جمع</TableCell><TableCell sx={{ fontWeight: 800 }}>{formatMoney(td)}</TableCell>
              <TableCell sx={{ fontWeight: 800 }}>{formatMoney(tc)}</TableCell><TableCell>{td === tc ? '✓ تراز' : '✗ نامتوازن'}</TableCell></TableRow>
          </TableBody>
        </Table>
      </TableContainer>
      <Dialog open={!!ledger} onClose={() => setLedger(null)} maxWidth="md" fullWidth>
        <DialogTitle>دفتر کل: {ledger?.name}</DialogTitle>
        <DialogContent>
          {ledgerQ.isLoading ? <Loading /> : !ledgerQ.data?.length ? <Empty /> : (
            <Table size="small">
              <TableHead><TableRow><TableCell>تاریخ</TableCell><TableCell>شرح</TableCell><TableCell>بدهکار</TableCell><TableCell>بستانکار</TableCell><TableCell>مانده</TableCell></TableRow></TableHead>
              <TableBody>{ledgerQ.data.map((l, i) => (
                <TableRow key={i}><TableCell>{formatDate(l.date)}</TableCell><TableCell>{l.description}</TableCell>
                  <TableCell>{l.debit ? formatMoney(l.debit) : ''}</TableCell><TableCell>{l.credit ? formatMoney(l.credit) : ''}</TableCell><TableCell>{formatMoney(l.balance)}</TableCell></TableRow>
              ))}</TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions><Button onClick={() => setLedger(null)}>بستن</Button></DialogActions>
      </Dialog>
      <Dialog open={create} onClose={() => setCreate(false)} maxWidth="xs" fullWidth>
        <DialogTitle>حساب جدید</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="کد حساب (۴ تا ۶ رقم)" value={acc.code} onChange={(e) => setAcc({ ...acc, code: e.target.value })} inputProps={{ dir: 'ltr' }} />
          <TextField label="نام حساب" value={acc.name} onChange={(e) => setAcc({ ...acc, name: e.target.value })} />
          <TextField select label="نوع" value={acc.type} onChange={(e) => setAcc({ ...acc, type: e.target.value })}>
            {Object.entries(accountType).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
          </TextField>
        </Stack></DialogContent>
        <DialogActions><Button onClick={() => setCreate(false)}>انصراف</Button>
          <Button variant="contained" onClick={() => createAcc.mutate()} disabled={!acc.code || !acc.name}>ایجاد</Button></DialogActions>
      </Dialog>
    </Card>
  );
}
