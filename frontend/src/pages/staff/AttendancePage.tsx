import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Button, Card, CardContent, Grid, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Attendance, Member, Page, ScanResult } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, PageHeader } from '../../components/common';
import { JalaliDateField } from '../../components/JalaliDateField';
import { MemberPicker } from '../../components/MemberPicker';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatTime, toIsoDate } from '../../utils/format';
import { entryMethod } from '../../utils/labels';

export default function AttendancePage() {
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const staff = hasRole('ADMIN', 'RECEPTIONIST');
  const [member, setMember] = useState<Member | null>(null);
  const [from, setFrom] = useState<string | null>(toIsoDate(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useState<string | null>(toIsoDate(new Date()));

  const present = useQuery({
    queryKey: ['present'],
    queryFn: () => api.get<Attendance[]>('/attendance/present').then((r) => r.data),
    refetchInterval: 30_000,
  });
  const history = useQuery({
    queryKey: ['attendance-history', from, to],
    queryFn: () => api.get<Page<Attendance>>('/attendance', { params: { from, to, size: 200 } }).then((r) => r.data),
    enabled: staff,
  });
  const invalidate = () => { qc.invalidateQueries({ queryKey: ['present'] }); qc.invalidateQueries({ queryKey: ['attendance-history'] }); };
  const checkIn = useMutation({
    mutationFn: (id: number) => api.post<ScanResult>(`/attendance/check-in/${id}`).then((r) => r.data),
    onSuccess: (r) => { notify(r.message); setMember(null); invalidate(); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const checkOut = useMutation({
    mutationFn: (id: number) => api.post<ScanResult>(`/attendance/${id}/check-out`).then((r) => r.data),
    onSuccess: (r) => { notify(r.message); invalidate(); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });

  return (
    <>
      <PageHeader title="حضور و غیاب" actions={staff && <Button variant="contained" component={RouterLink} to="/kiosk">کیوسک</Button>} />
      <Grid container spacing={2}>
        {staff && (
          <Grid size={12}>
            <Card><CardContent>
              <Typography fontWeight={700} gutterBottom>ثبت ورود دستی</Typography>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                <MemberPicker value={member} onChange={setMember} />
                <Button variant="contained" disabled={!member || checkIn.isPending} onClick={() => member && checkIn.mutate(member.id)} sx={{ minWidth: 120 }}>ثبت ورود</Button>
              </Stack>
            </CardContent></Card>
          </Grid>
        )}
        <Grid size={{ xs: 12, lg: staff ? 5 : 12 }}>
          <Card>
            <CardContent><Typography fontWeight={700}>حاضرین در باشگاه ({faDigits(present.data?.length ?? 0)} نفر)</Typography></CardContent>
            {present.isLoading ? <Loading /> : !present.data?.length ? <Empty text="کسی در باشگاه نیست" /> : (
              <Table size="small">
                <TableHead><TableRow><TableCell>عضو</TableCell><TableCell>ورود</TableCell>{staff && <TableCell />}</TableRow></TableHead>
                <TableBody>
                  {present.data.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell>{a.memberName}</TableCell><TableCell>{formatTime(a.checkInAt)}</TableCell>
                      {staff && <TableCell><Button size="small" onClick={() => checkOut.mutate(a.id)}>ثبت خروج</Button></TableCell>}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </Grid>
        {staff && (
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card>
              <CardContent>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
                  <Typography fontWeight={700} sx={{ flex: 1 }}>تاریخچه تردد</Typography>
                  <JalaliDateField label="از" value={from} onChange={setFrom} />
                  <JalaliDateField label="تا" value={to} onChange={setTo} />
                </Stack>
              </CardContent>
              {history.isLoading ? <Loading /> : !history.data?.content.length ? <Empty /> : (
                <TableContainer sx={{ maxHeight: 520 }}>
                  <Table size="small" stickyHeader>
                    <TableHead><TableRow><TableCell>عضو</TableCell><TableCell>تاریخ</TableCell><TableCell>ورود</TableCell><TableCell>خروج</TableCell><TableCell>روش</TableCell></TableRow></TableHead>
                    <TableBody>
                      {history.data.content.map((a) => (
                        <TableRow key={a.id}>
                          <TableCell>{a.memberName}</TableCell><TableCell>{formatDate(a.checkInAt)}</TableCell>
                          <TableCell>{formatTime(a.checkInAt)}</TableCell><TableCell>{a.checkOutAt ? formatTime(a.checkOutAt) : '—'}</TableCell>
                          <TableCell>{entryMethod[a.method]}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Card>
          </Grid>
        )}
      </Grid>
    </>
  );
}
