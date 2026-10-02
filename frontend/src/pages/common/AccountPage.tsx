import { useState, type FormEvent } from 'react';
import { Alert, Button, Card, CardContent, Stack, TextField } from '@mui/material';
import { api, errorMessage } from '../../api/client';
import { PageHeader } from '../../components/common';
import { useNotify } from '../../components/Notify';

export default function AccountPage() {
  const notify = useNotify();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (next.length < 8) return setError('رمز عبور جدید باید حداقل ۸ کاراکتر باشد');
    if (next !== repeat) return setError('تکرار رمز عبور مطابقت ندارد');
    try {
      await api.post('/auth/change-password', { currentPassword: current || null, newPassword: next });
      notify('رمز عبور تغییر کرد');
      setCurrent(''); setNext(''); setRepeat('');
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader title="تغییر رمز عبور" />
      <Card sx={{ maxWidth: 480 }}>
        <CardContent>
          <Stack component="form" spacing={2} onSubmit={submit}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField type="password" label="رمز عبور فعلی" value={current} onChange={(e) => setCurrent(e.target.value)}
              helperText="اگر تاکنون رمز تعیین نکرده‌اید خالی بگذارید" />
            <TextField type="password" label="رمز عبور جدید" value={next} onChange={(e) => setNext(e.target.value)} />
            <TextField type="password" label="تکرار رمز عبور جدید" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
            <Button type="submit" variant="contained">ذخیره</Button>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
