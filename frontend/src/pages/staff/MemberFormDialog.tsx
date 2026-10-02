import { useEffect, useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, MenuItem, Switch,
  TextField } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Member, User } from '../../api/types';
import { JalaliDateField } from '../../components/JalaliDateField';
import { isValidNationalCode, isValidPhone, latinDigits, normalizePhone } from '../../utils/format';

const empty = { fullName: '', phone: '', password: '', nationalCode: '', gender: '', birthDate: null as string | null,
  address: '', emergencyPhone: '', coachId: '' as string | number, cardNo: '', referralCode: '', goal: '', notes: '', active: true };

export function MemberFormDialog({ open, onClose, member, onSaved }: {
  open: boolean; onClose: () => void; member?: Member | null; onSaved: (m: Member) => void;
}) {
  const [form, setForm] = useState(empty);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { data: coaches } = useQuery({
    queryKey: ['coaches'],
    queryFn: () => api.get<User[]>('/users/by-role', { params: { role: 'COACH' } }).then((r) => r.data),
    enabled: open,
  });

  useEffect(() => {
    if (!open) return;
    setError(null);
    setForm(member ? {
      ...empty, fullName: member.fullName, phone: member.phone, nationalCode: member.nationalCode ?? '',
      gender: member.gender ?? '', birthDate: member.birthDate, address: member.address ?? '',
      emergencyPhone: member.emergencyPhone ?? '', coachId: member.coachId ?? '', cardNo: member.cardNo ?? '',
      goal: member.goal ?? '', notes: member.notes ?? '', active: member.active,
    } : empty);
  }, [open, member]);

  const set = (k: keyof typeof empty) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const submit = async () => {
    setError(null);
    if (!form.fullName.trim()) return setError('نام الزامی است');
    if (!isValidPhone(form.phone)) return setError('شماره موبایل نامعتبر است');
    if (form.nationalCode && !isValidNationalCode(form.nationalCode)) return setError('کد ملی نامعتبر است');
    if (form.emergencyPhone && !isValidPhone(form.emergencyPhone)) return setError('شماره اضطراری نامعتبر است');
    if (form.password && form.password.length < 8) return setError('رمز عبور باید حداقل ۸ کاراکتر باشد');
    const body = {
      ...form,
      phone: normalizePhone(form.phone),
      emergencyPhone: form.emergencyPhone ? normalizePhone(form.emergencyPhone) : null,
      nationalCode: form.nationalCode ? latinDigits(form.nationalCode) : null,
      password: form.password || null,
      gender: form.gender || null,
      coachId: form.coachId === '' ? null : Number(form.coachId),
      cardNo: form.cardNo || null,
      referralCode: form.referralCode || null,
    };
    setBusy(true);
    try {
      const { data } = member ? await api.put<Member>(`/members/${member.id}`, body) : await api.post<Member>('/members', body);
      onSaved(data);
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{member ? 'ویرایش عضو' : 'ثبت عضو جدید'}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 6 }}><TextField label="نام و نام خانوادگی" required value={form.fullName} onChange={set('fullName')} autoFocus /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}><TextField label="شماره موبایل" required value={form.phone} onChange={set('phone')} inputProps={{ dir: 'ltr', inputMode: 'tel' }} /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}><TextField label="کد ملی" value={form.nationalCode} onChange={set('nationalCode')} inputProps={{ dir: 'ltr', maxLength: 10 }} /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="جنسیت" value={form.gender} onChange={set('gender')}>
              <MenuItem value="">نامشخص</MenuItem><MenuItem value="MALE">آقا</MenuItem><MenuItem value="FEMALE">خانم</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}><JalaliDateField label="تاریخ تولد" value={form.birthDate} onChange={(v) => setForm({ ...form, birthDate: v })} /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}><TextField label="تلفن اضطراری" value={form.emergencyPhone} onChange={set('emergencyPhone')} inputProps={{ dir: 'ltr' }} /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}><TextField label="شماره کارت / RFID" value={form.cardNo} onChange={set('cardNo')} inputProps={{ dir: 'ltr' }} /></Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="مربی" value={form.coachId} onChange={set('coachId')}>
              <MenuItem value="">بدون مربی</MenuItem>
              {coaches?.map((c) => <MenuItem key={c.id} value={c.id}>{c.fullName}</MenuItem>)}
            </TextField>
          </Grid>
          {!member && <Grid size={{ xs: 12, sm: 6 }}><TextField label="رمز عبور پنل (اختیاری)" type="password" value={form.password} onChange={set('password')}
            helperText="بدون رمز، عضو می‌تواند با کد پیامکی وارد شود" /></Grid>}
          {!member && <Grid size={{ xs: 12, sm: 6 }}><TextField label="کد معرف (اختیاری)" value={form.referralCode} onChange={set('referralCode')} inputProps={{ dir: 'ltr' }} /></Grid>}
          <Grid size={12}><TextField label="هدف ورزشی" value={form.goal} onChange={set('goal')} /></Grid>
          <Grid size={12}><TextField label="آدرس" value={form.address} onChange={set('address')} /></Grid>
          <Grid size={12}><TextField label="یادداشت" value={form.notes} onChange={set('notes')} multiline minRows={2} /></Grid>
          {member && <Grid size={12}><FormControlLabel control={<Switch checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />} label="حساب فعال" /></Grid>}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>انصراف</Button>
        <Button variant="contained" onClick={submit} disabled={busy}>ذخیره</Button>
      </DialogActions>
    </Dialog>
  );
}
