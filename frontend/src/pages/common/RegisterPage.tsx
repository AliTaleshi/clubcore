import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Link, Stack, TextField, Typography } from '@mui/material';
import { api, errorMessage } from '../../api/client';
import type { Tokens } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { isValidPhone, normalizePhone } from '../../utils/format';
import { AuthShell } from './AuthShell';

export default function RegisterPage() {
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ fullName: '', phone: '', password: '', referralCode: params.get('ref') ?? '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.fullName.trim()) return setError('نام و نام خانوادگی را وارد کنید');
    if (!isValidPhone(form.phone)) return setError('شماره موبایل نامعتبر است');
    if (form.password.length < 8) return setError('رمز عبور باید حداقل ۸ کاراکتر باشد');
    setBusy(true);
    try {
      const { data } = await api.post<Tokens>('/auth/register', {
        ...form,
        phone: normalizePhone(form.phone),
        referralCode: form.referralCode.trim() || null,
      });
      loginWithTokens(data);
      navigate('/me/buy', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="ثبت‌نام عضو جدید">
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Stack component="form" spacing={2} onSubmit={submit} noValidate>
        <TextField label="نام و نام خانوادگی" value={form.fullName} onChange={set('fullName')} autoFocus />
        <TextField label="شماره موبایل" value={form.phone} onChange={set('phone')} inputProps={{ inputMode: 'tel', dir: 'ltr' }} />
        <TextField label="رمز عبور" type="password" value={form.password} onChange={set('password')}
          helperText="حداقل ۸ کاراکتر" inputProps={{ dir: 'ltr' }} autoComplete="new-password" />
        <TextField label="کد معرف (اختیاری)" value={form.referralCode} onChange={set('referralCode')} inputProps={{ dir: 'ltr' }} />
        <Button type="submit" variant="contained" size="large" disabled={busy}>ثبت‌نام</Button>
      </Stack>
      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        حساب دارید؟ <Link component={RouterLink} to="/login">وارد شوید</Link>
      </Typography>
    </AuthShell>
  );
}
