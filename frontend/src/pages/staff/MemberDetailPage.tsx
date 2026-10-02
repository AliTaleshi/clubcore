import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List,
  ListItem, ListItemText, MenuItem, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs,
  TextField, Typography, Accordion, AccordionSummary, AccordionDetails, IconButton } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Attendance, CrmActivity, Invoice, LoyaltySummary, LoyaltyTx, Membership, MemberSummary, Page,
  WorkoutProgram } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmDialog, Empty, Loading, Markdown, PageHeader, StatCard, StatusChip } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatDateTime, formatMoney, formatNumber, formatTime } from '../../utils/format';
import { activityType, entryMethod, gender, invoiceStatus, loyaltyReason, membershipStatus } from '../../utils/labels';
import { ProgramGeneratorDialog } from '../coach/ProgramGeneratorDialog';
import { MemberFormDialog } from './MemberFormDialog';
import { SellMembershipDialog } from './SellMembershipDialog';

export default function MemberDetailPage() {
  const id = Number(useParams().id);
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [tab, setTab] = useState('overview');
  const [edit, setEdit] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ['member', id],
    queryFn: () => api.get<MemberSummary>(`/members/${id}`).then((r) => r.data),
  });
  const checkIn = useMutation({
    mutationFn: () => api.post(`/attendance/check-in/${id}`),
    onSuccess: () => { notify('ورود ثبت شد'); qc.invalidateQueries({ queryKey: ['member', id] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });

  if (isLoading) return <Loading />;
  if (error || !data) return <Alert severity="error">{errorMessage(error)}</Alert>;
  const m = data.member;
  const staff = hasRole('ADMIN', 'RECEPTIONIST');
  const refresh = () => qc.invalidateQueries({ queryKey: ['member', id] });

  const tabs = [
    { key: 'overview', label: 'خلاصه', show: true },
    { key: 'memberships', label: 'اشتراک‌ها', show: true },
    { key: 'invoices', label: 'فاکتورها', show: hasRole('ADMIN', 'RECEPTIONIST', 'ACCOUNTANT') },
    { key: 'attendance', label: 'تردد', show: hasRole('ADMIN', 'RECEPTIONIST', 'COACH') },
    { key: 'loyalty', label: 'امتیازها', show: staff },
    { key: 'crm', label: 'ارتباطات', show: staff },
    { key: 'programs', label: 'برنامه تمرینی', show: hasRole('ADMIN', 'COACH') },
  ].filter((t) => t.show);

  return (
    <>
      <PageHeader title={m.fullName} subtitle={`شماره عضویت ${faDigits(m.membershipNo)} — ${faDigits(m.phone)}`}
        actions={staff && (
          <>
            <Button variant="outlined" onClick={() => setEdit(true)}>ویرایش</Button>
            <Button variant="contained" onClick={() => checkIn.mutate()} disabled={!data.currentMembership || data.inside || checkIn.isPending}>
              {data.inside ? 'در باشگاه است' : 'ثبت ورود'}
            </Button>
          </>
        )} />
      {!m.active && <Alert severity="warning" sx={{ mb: 2 }}>حساب این عضو غیرفعال است</Alert>}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        {tabs.map((t) => <Tab key={t.key} value={t.key} label={t.label} />)}
      </Tabs>
      {tab === 'overview' && <Overview data={data} />}
      {tab === 'memberships' && <MembershipsTab memberId={id} canManage={staff} onChange={refresh} />}
      {tab === 'invoices' && <InvoicesTab memberId={id} />}
      {tab === 'attendance' && <AttendanceTab memberId={id} />}
      {tab === 'loyalty' && <LoyaltyTab memberId={id} />}
      {tab === 'crm' && <CrmTab memberId={id} />}
      {tab === 'programs' && <ProgramsTab memberId={id} goal={m.goal ?? ''} />}
      <MemberFormDialog open={edit} onClose={() => setEdit(false)} member={m} onSaved={() => refresh()} />
    </>
  );
}

function Overview({ data }: { data: MemberSummary }) {
  const m = data.member;
  const ms = data.currentMembership;
  const rows: [string, string][] = [
    ['کد ملی', faDigits(m.nationalCode) || '—'], ['جنسیت', m.gender ? gender[m.gender] : '—'],
    ['تاریخ تولد', formatDate(m.birthDate)], ['تلفن اضطراری', faDigits(m.emergencyPhone) || '—'],
    ['شماره کارت', m.cardNo ?? '—'], ['مربی', m.coachName ?? '—'], ['هدف', m.goal ?? '—'],
    ['کد معرف', m.referralCode], ['تاریخ عضویت', formatDate(m.createdAt)], ['آدرس', m.address ?? '—'],
  ];
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 8 }}>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid size={{ xs: 6, sm: 3 }}><StatCard label="وضعیت" value={data.inside ? 'حاضر' : 'خارج'} /></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><StatCard label="حضور ۳۰ روز" value={faDigits(data.visitsLast30)} /></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><StatCard label="امتیاز" value={formatNumber(data.loyalty.balance)} hint={data.loyalty.tierTitle} /></Grid>
          <Grid size={{ xs: 6, sm: 3 }}><StatCard label="آخرین مراجعه" value={data.lastVisit ? formatDate(data.lastVisit) : '—'} /></Grid>
        </Grid>
        <Card><CardContent>
          <Grid container spacing={1.5}>
            {rows.map(([k, v]) => (
              <Grid key={k} size={{ xs: 12, sm: 6 }}>
                <Typography variant="body2" color="text.secondary">{k}</Typography><Typography>{v}</Typography>
              </Grid>
            ))}
          </Grid>
          {m.notes && <Alert severity="info" sx={{ mt: 2 }}>{m.notes}</Alert>}
        </CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, md: 4 }}>
        <Card><CardContent>
          <Typography fontWeight={700} gutterBottom>اشتراک قابل استفاده</Typography>
          {ms ? (
            <Stack spacing={1}>
              <Typography variant="h6">{ms.planName}</Typography>
              <Typography variant="body2">از {formatDate(ms.startDate)} تا {formatDate(ms.endDate)}</Typography>
              <Typography variant="body2">جلسات باقیمانده: {ms.sessionsRemaining === null ? 'نامحدود' : faDigits(ms.sessionsRemaining)}</Typography>
            </Stack>
          ) : <Alert severity="warning">{data.entryBlockReason}</Alert>}
        </CardContent></Card>
      </Grid>
    </Grid>
  );
}

function MembershipsTab({ memberId, canManage, onChange }: { memberId: number; canManage: boolean; onChange: () => void }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const [sell, setSell] = useState(false);
  const [confirm, setConfirm] = useState<{ ms: Membership; action: 'freeze' | 'unfreeze' | 'cancel' } | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['memberships', memberId],
    queryFn: () => api.get<Membership[]>(`/members/${memberId}/memberships`).then((r) => r.data),
  });
  const act = useMutation({
    mutationFn: ({ ms, action }: { ms: Membership; action: string }) => api.post(`/memberships/${ms.id}/${action}`),
    onSuccess: () => { notify('انجام شد'); setConfirm(null); qc.invalidateQueries({ queryKey: ['memberships', memberId] }); onChange(); },
    onError: (e) => { notify(errorMessage(e), 'error'); setConfirm(null); },
  });
  const actionText = { freeze: 'فریز', unfreeze: 'خروج از فریز', cancel: 'لغو' };
  return (
    <Card>
      {canManage && <Box sx={{ p: 2 }}><Button variant="contained" onClick={() => setSell(true)}>فروش اشتراک</Button></Box>}
      {isLoading ? <Loading /> : !data?.length ? <Empty text="اشتراکی ثبت نشده است" /> : (
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              <TableCell>پلن</TableCell><TableCell>شروع</TableCell><TableCell>پایان</TableCell><TableCell>جلسات</TableCell>
              <TableCell>فریز</TableCell><TableCell>مبلغ</TableCell><TableCell>وضعیت</TableCell>{canManage && <TableCell />}
            </TableRow></TableHead>
            <TableBody>
              {data.map((ms) => (
                <TableRow key={ms.id}>
                  <TableCell>{ms.planName}</TableCell>
                  <TableCell>{formatDate(ms.startDate)}</TableCell>
                  <TableCell>{formatDate(ms.endDate)}</TableCell>
                  <TableCell>{ms.sessionsTotal ? `${faDigits(ms.sessionsUsed)} از ${faDigits(ms.sessionsTotal)}` : 'نامحدود'}</TableCell>
                  <TableCell>{faDigits(ms.freezeDaysUsed)}/{faDigits(ms.maxFreezeDays)}</TableCell>
                  <TableCell>{formatMoney(ms.price - ms.discount)}</TableCell>
                  <TableCell><StatusChip map={membershipStatus} value={ms.status} /></TableCell>
                  {canManage && (
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {ms.status === 'ACTIVE' && ms.maxFreezeDays > ms.freezeDaysUsed &&
                        <Button size="small" onClick={() => setConfirm({ ms, action: 'freeze' })}>فریز</Button>}
                      {ms.status === 'FROZEN' && <Button size="small" onClick={() => setConfirm({ ms, action: 'unfreeze' })}>خروج از فریز</Button>}
                      {['ACTIVE', 'FROZEN', 'PENDING_PAYMENT'].includes(ms.status) &&
                        <Button size="small" color="error" onClick={() => setConfirm({ ms, action: 'cancel' })}>لغو</Button>}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <SellMembershipDialog open={sell} onClose={() => setSell(false)} memberId={memberId}
        onDone={() => { qc.invalidateQueries({ queryKey: ['memberships', memberId] }); qc.invalidateQueries({ queryKey: ['invoices'] }); onChange(); }} />
      <ConfirmDialog open={!!confirm} title={confirm ? `${actionText[confirm.action]} اشتراک` : ''}
        text={confirm ? `آیا از ${actionText[confirm.action]} اشتراک «${confirm.ms.planName}» مطمئن هستید؟` : ''}
        color={confirm?.action === 'cancel' ? 'error' : 'primary'} loading={act.isPending}
        onClose={() => setConfirm(null)} onConfirm={() => confirm && act.mutate(confirm)} />
    </Card>
  );
}

export function InvoicesTab({ memberId }: { memberId: number }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const [pay, setPay] = useState<Invoice | null>(null);
  const [method, setMethod] = useState('POS');
  const { data, isLoading } = useQuery({
    queryKey: ['invoices', { memberId }],
    queryFn: () => api.get<Page<Invoice>>('/invoices', { params: { memberId, size: 100 } }).then((r) => r.data),
  });
  const payMut = useMutation({
    mutationFn: () => api.post(`/invoices/${pay!.id}/pay`, { method }),
    onSuccess: () => { notify('پرداخت ثبت شد'); setPay(null); qc.invalidateQueries({ queryKey: ['invoices'] }); qc.invalidateQueries({ queryKey: ['memberships', memberId] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  return (
    <Card>
      {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow><TableCell>شماره</TableCell><TableCell>شرح</TableCell><TableCell>مبلغ</TableCell><TableCell>تخفیف</TableCell>
              <TableCell>قابل پرداخت</TableCell><TableCell>تاریخ</TableCell><TableCell>وضعیت</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {data.content.map((i) => (
                <TableRow key={i.id}>
                  <TableCell>{faDigits(i.number)}</TableCell><TableCell>{i.title}</TableCell>
                  <TableCell>{formatMoney(i.amount)}</TableCell><TableCell>{formatMoney(i.discount)}</TableCell>
                  <TableCell>{formatMoney(i.total)}</TableCell><TableCell>{formatDate(i.createdAt)}</TableCell>
                  <TableCell><StatusChip map={invoiceStatus} value={i.status} /></TableCell>
                  <TableCell>{i.status === 'UNPAID' && <Button size="small" onClick={() => setPay(i)}>ثبت پرداخت</Button>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <Dialog open={!!pay} onClose={() => setPay(null)} maxWidth="xs" fullWidth>
        <DialogTitle>ثبت پرداخت حضوری</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>مبلغ {formatMoney(pay?.total)} بابت «{pay?.title}»</Typography>
          <TextField select label="روش پرداخت" value={method} onChange={(e) => setMethod(e.target.value)}>
            <MenuItem value="POS">کارتخوان</MenuItem><MenuItem value="CASH">نقدی</MenuItem>
          </TextField>
        </DialogContent>
        <DialogActions><Button onClick={() => setPay(null)}>انصراف</Button>
          <Button variant="contained" onClick={() => payMut.mutate()} disabled={payMut.isPending}>ثبت</Button></DialogActions>
      </Dialog>
    </Card>
  );
}

function AttendanceTab({ memberId }: { memberId: number }) {
  const { data, isLoading } = useQuery({
    queryKey: ['attendance', memberId],
    queryFn: () => api.get<Page<Attendance>>('/attendance', { params: { memberId, size: 100 } }).then((r) => r.data),
  });
  return (
    <Card>
      {isLoading ? <Loading /> : !data?.content.length ? <Empty text="در ۳۰ روز اخیر مراجعه‌ای ثبت نشده" /> : (
        <Table size="small">
          <TableHead><TableRow><TableCell>تاریخ</TableCell><TableCell>ورود</TableCell><TableCell>خروج</TableCell><TableCell>روش</TableCell></TableRow></TableHead>
          <TableBody>
            {data.content.map((a) => (
              <TableRow key={a.id}><TableCell>{formatDate(a.checkInAt)}</TableCell><TableCell>{formatTime(a.checkInAt)}</TableCell>
                <TableCell>{a.checkOutAt ? formatTime(a.checkOutAt) : 'حاضر'}</TableCell><TableCell>{entryMethod[a.method]}</TableCell></TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}

function LoyaltyTab({ memberId }: { memberId: number }) {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [points, setPoints] = useState('');
  const [note, setNote] = useState('');
  const { data, isLoading } = useQuery({
    queryKey: ['loyalty', memberId],
    queryFn: () => api.get<{ summary: LoyaltySummary; history: LoyaltyTx[] }>(`/loyalty/members/${memberId}`).then((r) => r.data),
  });
  const adjust = useMutation({
    mutationFn: () => api.post('/loyalty/adjust', { memberId, points: Number(points), note }),
    onSuccess: () => { notify('امتیاز ثبت شد'); setPoints(''); setNote(''); qc.invalidateQueries({ queryKey: ['loyalty', memberId] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  if (isLoading || !data) return <Loading />;
  const s = data.summary;
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Stack spacing={2}>
          <StatCard label="امتیاز" value={formatNumber(s.balance)} hint={`سطح ${s.tierTitle} — ${faDigits(s.referrals)} معرفی`} />
          {hasRole('ADMIN') && (
            <Card><CardContent>
              <Typography fontWeight={700} gutterBottom>اصلاح دستی امتیاز</Typography>
              <Stack spacing={1.5}>
                <TextField label="امتیاز (+/−)" value={points} onChange={(e) => setPoints(e.target.value)} inputProps={{ dir: 'ltr' }} />
                <TextField label="توضیح" value={note} onChange={(e) => setNote(e.target.value)} />
                <Button variant="contained" onClick={() => adjust.mutate()} disabled={!points || !note || Number.isNaN(Number(points))}>ثبت</Button>
              </Stack>
            </CardContent></Card>
          )}
        </Stack>
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        <Card>
          {!data.history.length ? <Empty /> : (
            <List dense>
              {data.history.map((t) => (
                <ListItem key={t.id} divider secondaryAction={<Typography dir="ltr" color={t.points > 0 ? 'success.main' : 'error.main'} fontWeight={700}>{t.points > 0 ? '+' : ''}{faDigits(t.points)}</Typography>}>
                  <ListItemText primary={loyaltyReason[t.reason] ?? t.reason} secondary={`${formatDateTime(t.createdAt)}${t.reason === 'ADJUST' ? ' — ' + t.reference : ''}`} />
                </ListItem>
              ))}
            </List>
          )}
        </Card>
      </Grid>
    </Grid>
  );
}

function CrmTab({ memberId }: { memberId: number }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const [type, setType] = useState('CALL');
  const [content, setContent] = useState('');
  const { data } = useQuery({
    queryKey: ['member-activities', memberId],
    queryFn: () => api.get<CrmActivity[]>(`/crm/members/${memberId}/activities`).then((r) => r.data),
  });
  const add = useMutation({
    mutationFn: () => api.post('/crm/activities', { memberId, type, content }),
    onSuccess: () => { setContent(''); qc.invalidateQueries({ queryKey: ['member-activities', memberId] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}>
        <Card><CardContent><Stack spacing={1.5}>
          <Typography fontWeight={700}>ثبت فعالیت</Typography>
          <TextField select label="نوع" value={type} onChange={(e) => setType(e.target.value)}>
            {Object.entries(activityType).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
          </TextField>
          <TextField label="شرح" value={content} onChange={(e) => setContent(e.target.value)} multiline minRows={3} />
          <Button variant="contained" onClick={() => add.mutate()} disabled={!content.trim()}>ثبت</Button>
        </Stack></CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        <Card>
          {!data?.length ? <Empty text="فعالیتی ثبت نشده" /> : (
            <List>
              {data.map((a) => (
                <ListItem key={a.id} divider>
                  <ListItemText primary={<Stack direction="row" spacing={1} alignItems="center"><Chip size="small" label={activityType[a.type]} /><Typography variant="caption">{formatDateTime(a.createdAt)}</Typography></Stack>}
                    secondary={a.content} secondaryTypographyProps={{ sx: { mt: 0.5, whiteSpace: 'pre-wrap' } }} />
                </ListItem>
              ))}
            </List>
          )}
        </Card>
      </Grid>
    </Grid>
  );
}

function ProgramsTab({ memberId, goal }: { memberId: number; goal: string }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['programs', memberId],
    queryFn: () => api.get<WorkoutProgram[]>(`/members/${memberId}/programs`).then((r) => r.data),
  });
  const del = useMutation({
    mutationFn: (id: number) => api.delete(`/programs/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['programs', memberId] }),
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  return (
    <>
      <Button variant="contained" sx={{ mb: 2 }} onClick={() => setOpen(true)}>برنامه جدید با هوش مصنوعی</Button>
      {isLoading ? <Loading /> : !data?.length ? <Empty text="برنامه‌ای ثبت نشده" /> : data.map((p) => (
        <Accordion key={p.id}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography fontWeight={700} sx={{ flex: 1 }}>{p.title} <Typography component="span" variant="caption" color="text.secondary">({formatDate(p.createdAt)})</Typography></Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Markdown>{p.content}</Markdown>
            <IconButton color="error" onClick={() => del.mutate(p.id)} aria-label="حذف برنامه"><DeleteOutline /></IconButton>
          </AccordionDetails>
        </Accordion>
      ))}
      <ProgramGeneratorDialog open={open} onClose={() => setOpen(false)} memberId={memberId} defaultGoal={goal}
        onSaved={() => qc.invalidateQueries({ queryKey: ['programs', memberId] })} />
    </>
  );
}
