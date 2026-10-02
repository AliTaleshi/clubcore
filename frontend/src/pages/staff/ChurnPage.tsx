import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Card, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, LinearProgress, Link,
  Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, ToggleButton, ToggleButtonGroup,
  Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { MemberRisk } from '../../api/types';
import { Empty, Loading, PageHeader, StatCard, StatusChip } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate } from '../../utils/format';
import { churnLevel } from '../../utils/labels';

export default function ChurnPage() {
  const notify = useNotify();
  const [level, setLevel] = useState<'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'>('HIGH');
  const [target, setTarget] = useState<MemberRisk | null>(null);
  const [message, setMessage] = useState('');
  const [source, setSource] = useState('');
  const { data, isLoading } = useQuery({ queryKey: ['churn'], queryFn: () => api.get<MemberRisk[]>('/ai/churn').then((r) => r.data) });
  const counts = useMemo(() => ({
    HIGH: data?.filter((d) => d.level === 'HIGH').length ?? 0,
    MEDIUM: data?.filter((d) => d.level === 'MEDIUM').length ?? 0,
    LOW: data?.filter((d) => d.level === 'LOW').length ?? 0,
  }), [data]);
  const rows = data?.filter((d) => level === 'ALL' || d.level === level) ?? [];

  const draft = useMutation({
    mutationFn: (m: MemberRisk) => api.post<{ message: string; source: string }>(`/ai/retention-message/${m.memberId}`).then((r) => r.data),
    onSuccess: (r) => { setMessage(r.message); setSource(r.source); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const send = useMutation({
    mutationFn: () => api.post(`/ai/retention-message/${target!.memberId}/send`, { message }),
    onSuccess: () => { notify('پیامک ارسال و در پرونده CRM ثبت شد'); setTarget(null); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const openDraft = (m: MemberRisk) => { setTarget(m); setMessage(''); draft.mutate(m); };

  return (
    <>
      <PageHeader title="رادار ریزش اعضا" subtitle="پیش‌بینی احتمال عدم تمدید بر اساس الگوی حضور، زمان انقضا و سابقه هر عضو" />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 4 }}><StatCard label="ریسک زیاد" value={faDigits(counts.HIGH)} color="error.main" /></Grid>
        <Grid size={{ xs: 4 }}><StatCard label="ریسک متوسط" value={faDigits(counts.MEDIUM)} /></Grid>
        <Grid size={{ xs: 4 }}><StatCard label="ریسک کم" value={faDigits(counts.LOW)} /></Grid>
      </Grid>
      <Card>
        <Box sx={{ p: 2 }}>
          <ToggleButtonGroup exclusive size="small" value={level} onChange={(_, v) => v && setLevel(v)}>
            <ToggleButton value="HIGH">زیاد</ToggleButton><ToggleButton value="MEDIUM">متوسط</ToggleButton>
            <ToggleButton value="LOW">کم</ToggleButton><ToggleButton value="ALL">همه</ToggleButton>
          </ToggleButtonGroup>
        </Box>
        {isLoading ? <Loading /> : !rows.length ? <Empty /> : (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow><TableCell>عضو</TableCell><TableCell sx={{ minWidth: 140 }}>ریسک</TableCell><TableCell>دلایل</TableCell>
                <TableCell>آخرین مراجعه</TableCell><TableCell>پایان اشتراک</TableCell><TableCell /></TableRow></TableHead>
              <TableBody>{rows.map((m) => (
                <TableRow key={m.memberId}>
                  <TableCell><Link component={RouterLink} to={`/members/${m.memberId}`}>{m.fullName}</Link>
                    <Typography variant="caption" display="block" color="text.secondary">{faDigits(m.phone)}</Typography></TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <LinearProgress variant="determinate" value={m.risk} color={m.level === 'HIGH' ? 'error' : m.level === 'MEDIUM' ? 'warning' : 'success'} sx={{ flex: 1, height: 8, borderRadius: 4 }} />
                      <Typography variant="body2">{faDigits(m.risk)}٪</Typography>
                    </Stack>
                    <StatusChip map={churnLevel} value={m.level} />
                  </TableCell>
                  <TableCell><Stack spacing={0.5}>{m.reasons.map((r) => <Chip key={r} size="small" variant="outlined" label={faDigits(r)} sx={{ justifyContent: 'flex-start', height: 'auto', '& .MuiChip-label': { whiteSpace: 'normal', py: 0.3 } }} />)}</Stack></TableCell>
                  <TableCell>{formatDate(m.lastVisit)}</TableCell><TableCell>{formatDate(m.membershipEnd)}</TableCell>
                  <TableCell><Button size="small" variant="outlined" onClick={() => openDraft(m)}>پیام نگهداشت</Button></TableCell>
                </TableRow>
              ))}</TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      <Dialog open={!!target} onClose={() => setTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle>پیامک نگهداشت برای {target?.fullName}</DialogTitle>
        <DialogContent>
          {draft.isPending ? <Loading /> : (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Alert severity="info">{source === 'claude' ? 'متن با هوش مصنوعی و بر اساس دلایل ریزش این عضو نوشته شده است؛ قبل از ارسال بازبینی کنید.' : 'متن پیشنهادی (قالب داخلی)'}</Alert>
              <TextField multiline minRows={3} value={message} onChange={(e) => setMessage(e.target.value)} helperText={`${faDigits(message.length)} نویسه`} />
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => target && draft.mutate(target)} disabled={draft.isPending}>پیشنهاد دیگر</Button>
          <Button onClick={() => setTarget(null)}>انصراف</Button>
          <Button variant="contained" onClick={() => send.mutate()} disabled={!message.trim() || send.isPending}>ارسال پیامک</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
