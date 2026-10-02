import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Link, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { api, errorMessage } from '../../api/client';
import type { Tokens } from '../../api/types';
import { homeFor, useAuth } from '../../auth/AuthContext';
import { faDigits, isValidPhone, latinDigits, normalizePhone } from '../../utils/format';
import { AuthShell } from './AuthShell';

export default function LoginPage() {
  const { user, login, loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [tab, setTab] = useState(0);
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  if (user) return <Navigate to={homeFor(user.role)} replace />;

  const go = (role: Parameters<typeof homeFor>[0]) => {
    const from = (location.state as { from?: string } | null)?.from;
    navigate(from && from !== '/login' ? from : homeFor(role), { replace: true });
  };

  const submitPassword = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!isValidPhone(phone)) return setError('شماره موبایل را به‌درستی وارد کنید');
    setBusy(true);
    try {
      const u = await login(normalizePhone(phone), password);
      go(u.role);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const requestOtp = async () => {
    setError(null);
    if (!isValidPhone(phone)) return setError('شماره موبایل را به‌درستی وارد کنید');
    setBusy(true);
    try {
      const { data } = await api.post<{ message: string }>('/auth/otp/request', { phone: normalizePhone(phone) });
      setOtpSent(true);
      setCooldown(60);
      setInfo(data.message);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const verifyOtp = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const { data } = await api.post<Tokens>('/auth/otp/verify', { phone: normalizePhone(phone), code: latinDigits(code) });
      go(loginWithTokens(data).role);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="ورود به حساب کاربری">
      <Tabs value={tab} onChange={(_, v) => { setTab(v); setError(null); setInfo(null); }} variant="fullWidth" sx={{ mb: 2 }}>
        <Tab label="رمز عبور" />
        <Tab label="کد یکبار مصرف" />
      </Tabs>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {info && !error && <Alert severity="info" sx={{ mb: 2 }}>{info}</Alert>}
      {tab === 0 ? (
        <Stack component="form" spacing={2} onSubmit={submitPassword} noValidate>
          <TextField label="شماره موبایل" value={phone} onChange={(e) => setPhone(e.target.value)} autoFocus
            inputProps={{ inputMode: 'tel', dir: 'ltr' }} autoComplete="username" />
          <TextField label="رمز عبور" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password" inputProps={{ dir: 'ltr' }} />
          <Button type="submit" variant="contained" size="large" disabled={busy}>ورود</Button>
        </Stack>
      ) : (
        <Stack component="form" spacing={2} onSubmit={verifyOtp} noValidate>
          <TextField label="شماره موبایل" value={phone} onChange={(e) => setPhone(e.target.value)}
            inputProps={{ inputMode: 'tel', dir: 'ltr' }} disabled={otpSent} />
          {otpSent && (
            <TextField label="کد تأیید" value={code} onChange={(e) => setCode(e.target.value)} autoFocus
              inputProps={{ inputMode: 'numeric', dir: 'ltr', maxLength: 6 }} autoComplete="one-time-code" />
          )}
          {otpSent ? (
            <>
              <Button type="submit" variant="contained" size="large" disabled={busy || !code}>تأیید و ورود</Button>
              <Button onClick={requestOtp} disabled={busy || cooldown > 0}>
                {cooldown > 0 ? `ارسال مجدد (${faDigits(cooldown)} ثانیه)` : 'ارسال مجدد کد'}
              </Button>
            </>
          ) : (
            <Button variant="contained" size="large" onClick={requestOtp} disabled={busy}>دریافت کد</Button>
          )}
        </Stack>
      )}
      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        عضو نیستید؟ <Link component={RouterLink} to="/register">ثبت‌نام کنید</Link>
      </Typography>
    </AuthShell>
  );
}
