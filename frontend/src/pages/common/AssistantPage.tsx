import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Alert, Avatar, Box, Button, Card, Chip, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import SendRounded from '@mui/icons-material/SendRounded';
import SmartToyOutlined from '@mui/icons-material/SmartToyOutlined';
import DeleteSweepOutlined from '@mui/icons-material/DeleteSweepOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { AiMessage, AiReply } from '../../api/types';
import { Markdown, PageHeader } from '../../components/common';
import { useAuth } from '../../auth/AuthContext';
import { useNotify } from '../../components/Notify';

const SUGGESTIONS: Record<string, string[]> = {
  MEMBER: ['برای کاهش وزن هفته‌ای چند جلسه تمرین کنم؟', 'قبل از تمرین چه بخورم؟', 'پیشرفتم در این ماه چطور بوده؟'],
  COACH: ['کدام شاگردانم مدتی است نیامده‌اند؟', 'یک برنامه گرم‌کردن ۱۰ دقیقه‌ای پیشنهاد بده'],
  DEFAULT: ['وضعیت باشگاه در ۳۰ روز اخیر چطور است؟', 'برای کاهش ریزش اعضا چه کنم؟', 'یک ایده کمپین برای جذب عضو جدید بده'],
};

export default function AssistantPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const notify = useNotify();
  const [text, setText] = useState('');
  const [pending, setPending] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  const { data: status } = useQuery({
    queryKey: ['ai-status'],
    queryFn: () => api.get<{ enabled: boolean; model: string }>('/ai/status').then((r) => r.data),
  });
  const { data: history = [] } = useQuery({
    queryKey: ['ai-history'],
    queryFn: () => api.get<AiMessage[]>('/ai/chat/history').then((r) => r.data),
  });

  const send = useMutation({
    mutationFn: (message: string) => api.post<AiReply>('/ai/chat', { message }).then((r) => r.data),
    onMutate: (m) => setPending(m),
    onSettled: () => setPending(null),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ai-history'] }),
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const clear = useMutation({
    mutationFn: () => api.delete('/ai/chat/history'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ai-history'] }),
  });

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [history.length, pending]);

  const submit = (e?: FormEvent, value = text) => {
    e?.preventDefault();
    const m = value.trim();
    if (!m || send.isPending) return;
    setText('');
    send.mutate(m);
  };

  const suggestions = SUGGESTIONS[user?.role === 'MEMBER' || user?.role === 'COACH' ? user.role : 'DEFAULT'];

  return (
    <>
      <PageHeader title="دستیار هوشمند" subtitle="پاسخ‌ها بر اساس اطلاعات واقعی باشگاه و حساب شما تولید می‌شوند"
        actions={
          <Stack direction="row" spacing={1} alignItems="center">
            {status && <Chip size="small" color={status.enabled ? 'success' : 'default'}
              label={status.enabled ? 'متصل به هوش مصنوعی' : 'حالت آفلاین (پاسخ‌های داخلی)'} />}
            <Tooltip title="پاک کردن گفتگو">
              <IconButton onClick={() => clear.mutate()} aria-label="پاک کردن گفتگو"><DeleteSweepOutlined /></IconButton>
            </Tooltip>
          </Stack>
        } />
      <Card sx={{ display: 'flex', flexDirection: 'column', height: { xs: 'calc(100vh - 220px)', md: 'calc(100vh - 210px)' } }}>
        <Box sx={{ flex: 1, overflowY: 'auto', p: 2 }}>
          {history.length === 0 && !pending && (
            <Stack alignItems="center" spacing={2} sx={{ py: 4 }}>
              <Avatar sx={{ bgcolor: 'primary.main', width: 56, height: 56 }}><SmartToyOutlined /></Avatar>
              <Typography color="text.secondary">سؤال خود را بپرسید یا یکی از پیشنهادها را انتخاب کنید</Typography>
              <Stack direction="row" flexWrap="wrap" gap={1} justifyContent="center">
                {suggestions.map((s) => <Chip key={s} label={s} onClick={() => submit(undefined, s)} variant="outlined" />)}
              </Stack>
            </Stack>
          )}
          {history.map((m) => <Bubble key={m.id} role={m.role} content={m.content} />)}
          {pending && <Bubble role="user" content={pending} />}
          {pending && <Bubble role="assistant" content="در حال فکر کردن…" muted />}
          <div ref={bottom} />
        </Box>
        {status && !status.enabled && (
          <Alert severity="info" sx={{ mx: 2, mb: 1 }}>کلید API هوش مصنوعی تنظیم نشده؛ پاسخ‌ها از قواعد داخلی تولید می‌شوند.</Alert>
        )}
        <Stack component="form" direction="row" spacing={1} sx={{ p: 2, borderTop: 1, borderColor: 'divider' }} onSubmit={submit}>
          <TextField placeholder="پیام خود را بنویسید…" value={text} onChange={(e) => setText(e.target.value)}
            multiline maxRows={4} inputProps={{ 'aria-label': 'پیام' }}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); } }} />
          <Button type="submit" variant="contained" disabled={!text.trim() || send.isPending} aria-label="ارسال">
            <SendRounded sx={{ transform: 'scaleX(-1)' }} />
          </Button>
        </Stack>
      </Card>
    </>
  );
}

function Bubble({ role, content, muted }: { role: string; content: string; muted?: boolean }) {
  const mine = role === 'user';
  return (
    <Stack direction="row" justifyContent={mine ? 'flex-start' : 'flex-end'} sx={{ mb: 1.5 }}>
      <Paper elevation={0} sx={{ px: 2, py: 1, maxWidth: '85%', bgcolor: mine ? 'primary.main' : 'action.hover',
        color: mine ? 'primary.contrastText' : 'text.primary', opacity: muted ? 0.7 : 1, borderRadius: 3 }}>
        {mine ? <Typography sx={{ whiteSpace: 'pre-wrap' }}>{content}</Typography> : <Markdown>{content}</Markdown>}
      </Paper>
    </Stack>
  );
}
