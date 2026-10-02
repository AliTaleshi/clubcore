import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List,
  ListItem, ListItemText, MenuItem, Paper, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, TextField,
  Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Campaign, CrmActivity, Lead, Member, Page, User } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, PageHeader, StatCard, StatusChip } from '../../components/common';
import { JalaliDateField } from '../../components/JalaliDateField';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatDateTime, isValidPhone, normalizePhone } from '../../utils/format';
import { activityType, leadStatus } from '../../utils/labels';

const SOURCES = ['اینستاگرام', 'معرفی دوستان', 'گوگل', 'حضوری', 'بنر محیطی', 'تماس تلفنی', 'سایر'];

export default function CrmPage() {
  const [tab, setTab] = useState('pipeline');
  return (
    <>
      <PageHeader title="مدیریت ارتباط با مشتری" subtitle="سرنخ‌ها، پیگیری‌ها و کمپین‌های پیامکی" />
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="pipeline" label="قیف فروش" />
        <Tab value="due" label="پیگیری‌های امروز" />
        <Tab value="campaigns" label="بخش‌بندی و کمپین" />
      </Tabs>
      {tab === 'pipeline' && <Pipeline />}
      {tab === 'due' && <Due />}
      {tab === 'campaigns' && <Campaigns />}
    </>
  );
}

function Pipeline() {
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<Lead | null>(null);
  const [create, setCreate] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['leads', q],
    queryFn: () => api.get<Page<Lead>>('/crm/leads', { params: { q, size: 100 } }).then((r) => r.data),
  });
  const { data: stats } = useQuery({
    queryKey: ['lead-stats'],
    queryFn: () => api.get<{ total: number; conversionRate: number; byStatus: Record<string, number> }>('/crm/leads/stats').then((r) => r.data),
  });
  return (
    <>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="کل سرنخ‌ها" value={faDigits(stats?.total ?? 0)} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="نرخ تبدیل" value={`${faDigits(stats?.conversionRate ?? 0)}٪`} /></Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Stack direction="row" spacing={1} sx={{ height: '100%' }} alignItems="center">
            <TextField placeholder="جستجوی سرنخ" value={q} onChange={(e) => setQ(e.target.value)} />
            <Button variant="contained" onClick={() => setCreate(true)} sx={{ minWidth: 120 }}>سرنخ جدید</Button>
          </Stack>
        </Grid>
      </Grid>
      {isLoading ? <Loading /> : (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(5, minmax(0, 1fr))' }, gap: 1.5 }}>
          {Object.keys(leadStatus).map((s) => {
            const items = data?.content.filter((l) => l.status === s) ?? [];
            return (
              <Paper key={s} variant="outlined" sx={{ p: 1, bgcolor: 'action.hover', minHeight: 200 }}>
                <Stack direction="row" justifyContent="space-between" sx={{ mb: 1, px: 0.5 }}>
                  <StatusChip map={leadStatus} value={s} />
                  <Typography variant="caption">{faDigits(items.length)}</Typography>
                </Stack>
                {items.map((l) => (
                  <Card key={l.id} sx={{ mb: 1, cursor: 'pointer' }} onClick={() => setSelected(l)}>
                    <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                      <Typography fontWeight={700} variant="body2">{l.fullName}</Typography>
                      <Typography variant="caption" color="text.secondary" display="block">{faDigits(l.phone)} {l.source && `— ${l.source}`}</Typography>
                      {l.followUpDate && <Typography variant="caption" color="warning.main">پیگیری: {formatDate(l.followUpDate)}</Typography>}
                    </CardContent>
                  </Card>
                ))}
              </Paper>
            );
          })}
        </Box>
      )}
      <LeadDialog open={create} onClose={() => setCreate(false)} lead={null} />
      <LeadDialog open={!!selected} onClose={() => setSelected(null)} lead={selected} />
    </>
  );
}

function LeadDialog({ open, onClose, lead }: { open: boolean; onClose: () => void; lead: Lead | null }) {
  const qc = useQueryClient();
  const notify = useNotify();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', phone: '', source: '', status: 'NEW', interest: '', assignedTo: '' as string | number, followUpDate: null as string | null, notes: '' });
  const [activity, setActivity] = useState({ type: 'CALL', content: '' });
  const [error, setError] = useState<string | null>(null);
  const { data: staff } = useQuery({
    queryKey: ['receptionists'],
    queryFn: () => api.get<User[]>('/users/by-role', { params: { role: 'RECEPTIONIST' } }).then((r) => r.data),
    enabled: open,
  });
  const { data: detail } = useQuery({
    queryKey: ['lead', lead?.id],
    queryFn: () => api.get<{ lead: Lead; activities: CrmActivity[] }>(`/crm/leads/${lead!.id}`).then((r) => r.data),
    enabled: open && !!lead,
  });
  useEffect(() => {
    if (!open) return;
    setError(null);
    setForm(lead ? { fullName: lead.fullName, phone: lead.phone, source: lead.source ?? '', status: lead.status, interest: lead.interest ?? '',
      assignedTo: lead.assignedTo ?? '', followUpDate: lead.followUpDate, notes: lead.notes ?? '' }
      : { fullName: '', phone: '', source: '', status: 'NEW', interest: '', assignedTo: '', followUpDate: null, notes: '' });
  }, [open, lead]);
  const invalidate = () => { qc.invalidateQueries({ queryKey: ['leads'] }); qc.invalidateQueries({ queryKey: ['lead-stats'] }); qc.invalidateQueries({ queryKey: ['lead'] }); qc.invalidateQueries({ queryKey: ['leads-due'] }); };
  const save = useMutation({
    mutationFn: () => {
      if (!isValidPhone(form.phone)) throw new Error('شماره موبایل نامعتبر است');
      const body = { ...form, phone: normalizePhone(form.phone), assignedTo: form.assignedTo === '' ? null : Number(form.assignedTo), source: form.source || null };
      return lead ? api.put(`/crm/leads/${lead.id}`, body) : api.post('/crm/leads', body);
    },
    onSuccess: () => { notify('سرنخ ذخیره شد'); invalidate(); onClose(); },
    onError: (e) => setError(e instanceof Error && !('isAxiosError' in e) ? e.message : errorMessage(e)),
  });
  const convert = useMutation({
    mutationFn: () => api.post<Member>(`/crm/leads/${lead!.id}/convert`).then((r) => r.data),
    onSuccess: (m) => { notify('سرنخ به عضو تبدیل شد'); invalidate(); onClose(); navigate(`/members/${m.id}`); },
    onError: (e) => setError(errorMessage(e)),
  });
  const addActivity = useMutation({
    mutationFn: () => api.post('/crm/activities', { leadId: lead!.id, ...activity }),
    onSuccess: () => { setActivity({ ...activity, content: '' }); qc.invalidateQueries({ queryKey: ['lead', lead?.id] }); },
  });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });
  const converted = lead?.status === 'CONVERTED';

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{lead ? lead.fullName : 'سرنخ جدید'}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, md: lead ? 6 : 12 }}>
            <Grid container spacing={2}>
              <Grid size={6}><TextField label="نام" value={form.fullName} onChange={set('fullName')} disabled={converted} /></Grid>
              <Grid size={6}><TextField label="موبایل" value={form.phone} onChange={set('phone')} disabled={converted} inputProps={{ dir: 'ltr' }} /></Grid>
              <Grid size={6}><TextField select label="منبع" value={form.source} onChange={set('source')} disabled={converted}>
                <MenuItem value="">نامشخص</MenuItem>{SOURCES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField></Grid>
              <Grid size={6}><TextField select label="وضعیت" value={form.status} onChange={set('status')} disabled={converted}>
                {Object.entries(leadStatus).filter(([k]) => k !== 'CONVERTED' || converted).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}</TextField></Grid>
              <Grid size={6}><TextField select label="مسئول پیگیری" value={form.assignedTo} onChange={set('assignedTo')} disabled={converted}>
                <MenuItem value="">—</MenuItem>{staff?.map((u) => <MenuItem key={u.id} value={u.id}>{u.fullName}</MenuItem>)}</TextField></Grid>
              <Grid size={6}><JalaliDateField label="تاریخ پیگیری" value={form.followUpDate} onChange={(v) => setForm({ ...form, followUpDate: v })} /></Grid>
              <Grid size={12}><TextField label="علاقه‌مندی" value={form.interest} onChange={set('interest')} disabled={converted} /></Grid>
              <Grid size={12}><TextField label="یادداشت" value={form.notes} onChange={set('notes')} multiline minRows={2} disabled={converted} /></Grid>
            </Grid>
          </Grid>
          {lead && (
            <Grid size={{ xs: 12, md: 6 }}>
              <Typography fontWeight={700} gutterBottom>تاریخچه فعالیت</Typography>
              <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                <TextField select value={activity.type} onChange={(e) => setActivity({ ...activity, type: e.target.value })} sx={{ maxWidth: 130 }}>
                  {Object.entries(activityType).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
                </TextField>
                <TextField placeholder="شرح فعالیت" value={activity.content} onChange={(e) => setActivity({ ...activity, content: e.target.value })} />
                <Button onClick={() => addActivity.mutate()} disabled={!activity.content.trim()}>ثبت</Button>
              </Stack>
              <List dense sx={{ maxHeight: 300, overflowY: 'auto' }}>
                {detail?.activities.map((a) => (
                  <ListItem key={a.id} divider>
                    <ListItemText primary={<><Chip size="small" label={activityType[a.type]} sx={{ ml: 1 }} />{a.content}</>} secondary={formatDateTime(a.createdAt)} />
                  </ListItem>
                ))}
              </List>
            </Grid>
          )}
        </Grid>
      </DialogContent>
      <DialogActions>
        {lead && !converted && <Button color="success" onClick={() => convert.mutate()} disabled={convert.isPending} sx={{ ml: 'auto' }}>تبدیل به عضو</Button>}
        <Button onClick={onClose}>بستن</Button>
        {!converted && <Button variant="contained" onClick={() => save.mutate()} disabled={!form.fullName || !form.phone || save.isPending}>ذخیره</Button>}
      </DialogActions>
    </Dialog>
  );
}

function Due() {
  const [selected, setSelected] = useState<Lead | null>(null);
  const { data, isLoading } = useQuery({ queryKey: ['leads-due'], queryFn: () => api.get<Lead[]>('/crm/leads/due').then((r) => r.data) });
  return (
    <Card>
      {isLoading ? <Loading /> : !data?.length ? <Empty text="پیگیری معوقی وجود ندارد" /> : (
        <Table>
          <TableHead><TableRow><TableCell>نام</TableCell><TableCell>موبایل</TableCell><TableCell>وضعیت</TableCell><TableCell>تاریخ پیگیری</TableCell><TableCell /></TableRow></TableHead>
          <TableBody>{data.map((l) => (
            <TableRow key={l.id}><TableCell>{l.fullName}</TableCell><TableCell>{faDigits(l.phone)}</TableCell>
              <TableCell><StatusChip map={leadStatus} value={l.status} /></TableCell><TableCell>{formatDate(l.followUpDate)}</TableCell>
              <TableCell><Button size="small" onClick={() => setSelected(l)}>پیگیری</Button></TableCell></TableRow>
          ))}</TableBody>
        </Table>
      )}
      <LeadDialog open={!!selected} onClose={() => setSelected(null)} lead={selected} />
    </Card>
  );
}

function Campaigns() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [segment, setSegment] = useState('EXPIRING_SOON');
  const [form, setForm] = useState({ title: '', message: '{name} عزیز، ' });
  const { data: segments } = useQuery({ queryKey: ['segments'], queryFn: () => api.get<{ key: string; title: string }[]>('/crm/segments').then((r) => r.data) });
  const members = useQuery({ queryKey: ['segment', segment], queryFn: () => api.get<Member[]>(`/crm/segments/${segment}/members`).then((r) => r.data) });
  const { data: campaigns } = useQuery({ queryKey: ['campaigns'], queryFn: () => api.get<Campaign[]>('/crm/campaigns').then((r) => r.data) });
  const send = useMutation({
    mutationFn: () => api.post<Campaign>('/crm/campaigns', { ...form, segment }).then((r) => r.data),
    onSuccess: (c) => { notify(`پیامک برای ${faDigits(c.sentCount)} نفر ارسال شد`); setForm({ title: '', message: '{name} عزیز، ' }); qc.invalidateQueries({ queryKey: ['campaigns'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const segTitle = (k: string) => segments?.find((s) => s.key === k)?.title ?? k;
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 7 }}>
        <Card><CardContent>
          <TextField select label="بخش مشتریان" value={segment} onChange={(e) => setSegment(e.target.value)} sx={{ mb: 2 }}>
            {segments?.map((s) => <MenuItem key={s.key} value={s.key}>{s.title}</MenuItem>)}
          </TextField>
          {members.isLoading ? <Loading /> : !members.data?.length ? <Empty text="عضوی در این بخش نیست" /> : (
            <>
              <Typography variant="body2" color="text.secondary" gutterBottom>{faDigits(members.data.length)} عضو</Typography>
              <Box sx={{ maxHeight: 420, overflowY: 'auto' }}>
                <Table size="small">
                  <TableHead><TableRow><TableCell>نام</TableCell><TableCell>موبایل</TableCell><TableCell>شماره عضویت</TableCell></TableRow></TableHead>
                  <TableBody>{members.data.map((m) => <TableRow key={m.id}><TableCell>{m.fullName}</TableCell><TableCell>{faDigits(m.phone)}</TableCell><TableCell>{faDigits(m.membershipNo)}</TableCell></TableRow>)}</TableBody>
                </Table>
              </Box>
            </>
          )}
        </CardContent></Card>
      </Grid>
      <Grid size={{ xs: 12, md: 5 }}>
        {hasRole('ADMIN') && (
          <Card sx={{ mb: 2 }}><CardContent>
            <Typography fontWeight={700} gutterBottom>ارسال کمپین پیامکی به «{segTitle(segment)}»</Typography>
            <Stack spacing={2}>
              <TextField label="عنوان کمپین" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
              <TextField label="متن پیامک" value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} multiline minRows={3}
                helperText={`${faDigits(form.message.length)} نویسه — {name} نام عضو و {gym} نام باشگاه`} />
              <Button variant="contained" onClick={() => send.mutate()} disabled={!form.title || !form.message || !members.data?.length || send.isPending}>
                ارسال برای {faDigits(members.data?.length ?? 0)} نفر
              </Button>
            </Stack>
          </CardContent></Card>
        )}
        <Card><CardContent>
          <Typography fontWeight={700} gutterBottom>کمپین‌های اخیر</Typography>
          {!campaigns?.length ? <Empty /> : (
            <List dense>{campaigns.map((c) => (
              <ListItem key={c.id} divider>
                <ListItemText primary={`${c.title} — ${faDigits(c.sentCount)} پیامک`} secondary={`${segTitle(c.segment)} — ${formatDateTime(c.createdAt)}`} />
              </ListItem>
            ))}</List>
          )}
        </CardContent></Card>
      </Grid>
    </Grid>
  );
}
