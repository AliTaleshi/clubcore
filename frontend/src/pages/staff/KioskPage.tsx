import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Alert, Box, Button, Card, CardContent, Grid, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import LoginRounded from '@mui/icons-material/LoginRounded';
import LogoutRounded from '@mui/icons-material/LogoutRounded';
import { api, errorMessage } from '../../api/client';
import type { ScanResult } from '../../api/types';
import { faDigits, formatDate, latinDigits } from '../../utils/format';

type Method = 'QR' | 'CARD' | 'MANUAL';
type Outcome = { ok: true; result: ScanResult } | { ok: false; message: string };

/** Camera QR scanner; html5-qrcode is loaded lazily so the page also works on devices without a camera. */
function CameraScanner({ onScan }: { onScan: (text: string) => void }) {
  const [error, setError] = useState<string | null>(null);
  const last = useRef<{ text: string; at: number }>({ text: '', at: 0 });
  useEffect(() => {
    let scanner: { stop: () => Promise<void>; clear: () => void } | null = null;
    let cancelled = false;
    import('html5-qrcode').then(({ Html5Qrcode }) => {
      if (cancelled) return;
      const s = new Html5Qrcode('kiosk-camera');
      scanner = s;
      s.start({ facingMode: 'environment' }, { fps: 8, qrbox: 240 }, (text) => {
        const now = Date.now();
        // Ignore the same code read repeatedly while it stays in front of the camera.
        if (text === last.current.text && now - last.current.at < 5000) return;
        last.current = { text, at: now };
        onScan(text);
      }, () => undefined).catch(() => setError('دسترسی به دوربین ممکن نیست؛ از کارت یا ورود دستی استفاده کنید'));
    });
    return () => {
      cancelled = true;
      scanner?.stop().then(() => scanner?.clear()).catch(() => undefined);
    };
  }, [onScan]);
  return (
    <>
      {error && <Alert severity="warning" sx={{ mb: 1 }}>{error}</Alert>}
      <Box id="kiosk-camera" sx={{ width: '100%', maxWidth: 420, mx: 'auto', borderRadius: 2, overflow: 'hidden' }} />
    </>
  );
}

export default function KioskPage() {
  const [method, setMethod] = useState<Method>('CARD');
  const [value, setValue] = useState('');
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const scan = useCallback(async (m: Method, v: string) => {
    const clean = latinDigits(v.trim());
    if (!clean) return;
    setBusy(true);
    try {
      const { data } = await api.post<ScanResult>('/attendance/scan', { method: m, value: clean });
      setOutcome({ ok: true, result: data });
    } catch (e) {
      setOutcome({ ok: false, message: errorMessage(e) });
    } finally {
      setBusy(false);
      setValue('');
      input.current?.focus();
    }
  }, []);

  const onQr = useCallback((text: string) => scan('QR', text), [scan]);

  useEffect(() => {
    if (!outcome) return;
    const t = setTimeout(() => setOutcome(null), 6000);
    return () => clearTimeout(t);
  }, [outcome]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    scan(method, value);
  };

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card>
          <Tabs value={method} onChange={(_, v) => { setMethod(v); setValue(''); }} variant="fullWidth">
            <Tab value="CARD" label="کارت / RFID" />
            <Tab value="QR" label="دوربین QR" />
            <Tab value="MANUAL" label="دستی" />
          </Tabs>
          <CardContent>
            {method === 'QR' ? (
              <>
                <CameraScanner onScan={onQr} />
                <Typography variant="body2" color="text.secondary" textAlign="center" sx={{ mt: 1 }}>
                  کد QR پنل عضو را مقابل دوربین بگیرید
                </Typography>
                <Stack component="form" onSubmit={submit} direction="row" spacing={1} sx={{ mt: 2 }}>
                  <TextField label="یا کد QR را وارد کنید (اسکنر دستی)" value={value} onChange={(e) => setValue(e.target.value)} inputRef={input} inputProps={{ dir: 'ltr' }} />
                  <Button type="submit" variant="contained" disabled={busy}>ثبت</Button>
                </Stack>
              </>
            ) : (
              <Stack component="form" onSubmit={submit} spacing={2}>
                <Typography color="text.secondary">
                  {method === 'CARD' ? 'کارت عضو را روی کارتخوان قرار دهید یا شماره کارت را وارد کنید' : 'شماره موبایل یا شماره عضویت را وارد کنید'}
                </Typography>
                <TextField autoFocus inputRef={input} value={value} onChange={(e) => setValue(e.target.value)}
                  label={method === 'CARD' ? 'شماره کارت' : 'موبایل / شماره عضویت'} inputProps={{ dir: 'ltr', 'aria-label': 'شناسه عضو' }}
                  sx={{ '& input': { fontSize: 24, textAlign: 'center' } }} />
                <Button type="submit" variant="contained" size="large" disabled={busy || !value.trim()}>ثبت ورود / خروج</Button>
              </Stack>
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card sx={{ minHeight: 320, display: 'grid', placeItems: 'center', textAlign: 'center',
          bgcolor: !outcome ? undefined : outcome.ok ? (outcome.result.action === 'CHECK_IN' ? 'success.main' : 'info.main') : 'error.main',
          color: outcome ? '#fff' : undefined, transition: 'background-color .3s' }} aria-live="polite">
          <CardContent>
            {!outcome && <Typography variant="h5" color="text.secondary">آماده اسکن…</Typography>}
            {outcome && !outcome.ok && <Typography variant="h5" fontWeight={800}>{outcome.message}</Typography>}
            {outcome?.ok && (
              <Stack alignItems="center" spacing={1}>
                {outcome.result.action === 'CHECK_IN' ? <LoginRounded sx={{ fontSize: 64 }} /> : <LogoutRounded sx={{ fontSize: 64 }} />}
                <Typography variant="h4" fontWeight={800}>{outcome.result.action === 'CHECK_IN' ? 'خوش آمدید' : 'خدا نگهدار'}</Typography>
                <Typography variant="h5">{outcome.result.memberName}</Typography>
                <Typography>{outcome.result.message}</Typography>
                {outcome.result.planName && (
                  <Typography variant="body2">
                    {outcome.result.planName} — اعتبار تا {formatDate(outcome.result.endDate)}
                    {outcome.result.sessionsRemaining !== null && ` — ${faDigits(outcome.result.sessionsRemaining)} جلسه باقیمانده`}
                  </Typography>
                )}
              </Stack>
            )}
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
