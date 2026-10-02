import { useEffect, useState } from 'react';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Grid, LinearProgress, MenuItem,
  Stack, TextField } from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { AiReply } from '../../api/types';
import { Markdown } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits } from '../../utils/format';

/** Generates a workout plan with AI (or the local fallback), lets the user edit it, then saves it to the member. */
export function ProgramGeneratorDialog({ open, onClose, memberId, defaultGoal, onSaved }: {
  open: boolean; onClose: () => void; memberId: number; defaultGoal: string; onSaved: () => void;
}) {
  const notify = useNotify();
  const [goal, setGoal] = useState(defaultGoal);
  const [level, setLevel] = useState('متوسط');
  const [days, setDays] = useState(3);
  const [notes, setNotes] = useState('');
  const [title, setTitle] = useState('');
  const [result, setResult] = useState<AiReply | null>(null);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    if (open) { setGoal(defaultGoal); setResult(null); setEditing(false); }
  }, [open, defaultGoal]);

  const generate = useMutation({
    mutationFn: () => api.post<AiReply>('/ai/workout-plan', { memberId, goal, level, daysPerWeek: days, notes }).then((r) => r.data),
    onSuccess: (r) => { setResult(r); setTitle(`برنامه ${goal || 'تمرینی'} - ${faDigits(days)} روزه`); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });
  const save = useMutation({
    mutationFn: () => api.post(`/members/${memberId}/programs`, { title, content: result!.content, aiGenerated: true }),
    onSuccess: () => { notify('برنامه ذخیره شد'); onSaved(); onClose(); },
    onError: (e) => notify(errorMessage(e), 'error'),
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>ساخت برنامه تمرینی هوشمند</DialogTitle>
      <DialogContent>
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, sm: 5 }}><TextField label="هدف" value={goal} onChange={(e) => setGoal(e.target.value)} /></Grid>
          <Grid size={{ xs: 6, sm: 4 }}>
            <TextField select label="سطح" value={level} onChange={(e) => setLevel(e.target.value)}>
              {['مبتدی', 'متوسط', 'پیشرفته'].map((l) => <MenuItem key={l} value={l}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <TextField select label="جلسه در هفته" value={days} onChange={(e) => setDays(Number(e.target.value))}>
              {[2, 3, 4, 5, 6].map((d) => <MenuItem key={d} value={d}>{faDigits(d)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={12}><TextField label="ملاحظات (آسیب‌دیدگی، تجهیزات، محدودیت زمانی…)" value={notes} onChange={(e) => setNotes(e.target.value)} /></Grid>
        </Grid>
        {generate.isPending && <Box sx={{ mt: 2 }}><LinearProgress /><Alert severity="info" sx={{ mt: 1 }}>در حال طراحی برنامه…</Alert></Box>}
        {result && (
          <Box sx={{ mt: 2 }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <Chip size="small" label={result.source === 'claude' ? 'تولید شده با هوش مصنوعی' : 'قالب داخلی (هوش مصنوعی در دسترس نیست)'}
                color={result.source === 'claude' ? 'primary' : 'default'} />
              <Button size="small" onClick={() => setEditing((e) => !e)}>{editing ? 'پیش‌نمایش' : 'ویرایش متن'}</Button>
            </Stack>
            <TextField label="عنوان برنامه" value={title} onChange={(e) => setTitle(e.target.value)} sx={{ mb: 2 }} />
            {editing ? (
              <TextField multiline minRows={12} value={result.content} onChange={(e) => setResult({ ...result, content: e.target.value })} />
            ) : (
              <Box sx={{ maxHeight: 400, overflowY: 'auto', border: 1, borderColor: 'divider', borderRadius: 2, p: 2 }}>
                <Markdown>{result.content}</Markdown>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>بستن</Button>
        <Button onClick={() => generate.mutate()} disabled={generate.isPending}>{result ? 'تولید مجدد' : 'تولید برنامه'}</Button>
        <Button variant="contained" onClick={() => save.mutate()} disabled={!result || !title.trim() || save.isPending}>ذخیره برای عضو</Button>
      </DialogActions>
    </Dialog>
  );
}
