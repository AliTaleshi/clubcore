import { useEffect, useState } from 'react';
import { Button, Card, CardContent, Grid, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage, ValidationError } from '../../api/client';
import type { MemberSummary } from '../../api/types';
import { Loading, PageHeader } from '../../components/common';
import { JalaliDateField } from '../../components/JalaliDateField';
import { useNotify } from '../../components/Notify';
import { faDigits, isValidPhone, normalizePhone } from '../../utils/format';

export default function ProfilePage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data } = useQuery({ queryKey: ['me-member'], queryFn: () => api.get<MemberSummary>('/me/member').then((r) => r.data) });
  const [form, setForm] = useState({ fullName: '', gender: '', birthDate: null as string | null, address: '', emergencyPhone: '', goal: '' });
  useEffect(() => {
    if (data) {
      const m = data.member;
      setForm({ fullName: m.fullName, gender: m.gender ?? '', birthDate: m.birthDate, address: m.address ?? '',
        emergencyPhone: m.emergencyPhone ?? '', goal: m.goal ?? '' });
    }
  }, [data]);
  const save = useMutation({
    mutationFn: () => {
      if (!form.fullName.trim()) throw new ValidationError('نام الزامی است');
      const emergency = form.emergencyPhone.trim();
      if (emergency && !isValidPhone(emergency)) throw new ValidationError('شماره اضطراری نامعتبر است');
      return api.put('/me/member', {
        ...form,
        phone: data!.member.phone,
        gender: form.gender || null,
        emergencyPhone: emergency ? normalizePhone(emergency) : null,
      });
    },
    onSuccess: () => { notify('پروفایل ذخیره شد'); qc.invalidateQueries({ queryKey: ['me-member'] }); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  if (!data) return <Loading />;
  return (
    <>
      <PageHeader title="پروفایل من" />
      <Card sx={{ maxWidth: 720 }}>
        <CardContent>
          <Stack spacing={0.5} sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary">شماره موبایل: {faDigits(data.member.phone)}</Typography>
            <Typography variant="body2" color="text.secondary">شماره عضویت: {faDigits(data.member.membershipNo)}</Typography>
            {data.member.coachName && <Typography variant="body2" color="text.secondary">مربی: {data.member.coachName}</Typography>}
          </Stack>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}><TextField label="نام و نام خانوادگی" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField select label="جنسیت" value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}>
                <MenuItem value="">نامشخص</MenuItem><MenuItem value="MALE">آقا</MenuItem><MenuItem value="FEMALE">خانم</MenuItem>
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}><JalaliDateField label="تاریخ تولد" value={form.birthDate} onChange={(v) => setForm({ ...form, birthDate: v })} /></Grid>
            <Grid size={{ xs: 12, sm: 6 }}><TextField label="تلفن اضطراری" value={form.emergencyPhone} onChange={(e) => setForm({ ...form, emergencyPhone: e.target.value })} inputProps={{ dir: 'ltr' }} /></Grid>
            <Grid size={12}><TextField label="هدف ورزشی" value={form.goal} onChange={(e) => setForm({ ...form, goal: e.target.value })} placeholder="مثلاً کاهش وزن، افزایش حجم" /></Grid>
            <Grid size={12}><TextField label="آدرس" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} multiline minRows={2} /></Grid>
          </Grid>
          <Button variant="contained" sx={{ mt: 2 }} onClick={() => save.mutate()} disabled={save.isPending}>ذخیره</Button>
        </CardContent>
      </Card>
    </>
  );
}
