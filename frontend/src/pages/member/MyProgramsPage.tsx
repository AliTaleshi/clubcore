import { useState } from 'react';
import { Accordion, AccordionDetails, AccordionSummary, Button, Chip, Stack, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { MemberSummary, WorkoutProgram } from '../../api/types';
import { Empty, Loading, Markdown, PageHeader } from '../../components/common';
import { formatDate } from '../../utils/format';
import { ProgramGeneratorDialog } from '../coach/ProgramGeneratorDialog';

export default function MyProgramsPage() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['my-programs'],
    queryFn: () => api.get<WorkoutProgram[]>('/me/programs').then((r) => r.data),
  });
  const { data: me } = useQuery({
    queryKey: ['me-member'],
    queryFn: () => api.get<MemberSummary>('/me/member').then((r) => r.data),
  });
  return (
    <>
      <PageHeader title="برنامه‌های تمرینی" subtitle="برنامه‌هایی که مربی برای شما ثبت کرده یا با هوش مصنوعی ساخته‌اید"
        actions={<Button variant="contained" startIcon={<AutoAwesomeOutlined />} onClick={() => setOpen(true)} disabled={!me}>
          ساخت برنامه با هوش مصنوعی</Button>} />
      {isLoading ? <Loading /> : !data?.length ? <Empty text="هنوز برنامه‌ای ندارید" /> : data.map((p) => (
        <Accordion key={p.id} defaultExpanded={p.id === data[0].id}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography fontWeight={700}>{p.title}</Typography>
              <Typography variant="caption" color="text.secondary">{formatDate(p.createdAt)}</Typography>
              {p.coachId ? <Chip size="small" label="مربی" color="primary" /> : null}
              {p.aiGenerated && <Chip size="small" label="هوش مصنوعی" variant="outlined" />}
            </Stack>
          </AccordionSummary>
          <AccordionDetails><Markdown>{p.content}</Markdown></AccordionDetails>
        </Accordion>
      ))}
      {me && (
        <ProgramGeneratorDialog open={open} onClose={() => setOpen(false)} memberId={me.member.id}
          defaultGoal={me.member.goal ?? ''} onSaved={() => qc.invalidateQueries({ queryKey: ['my-programs'] })} />
      )}
    </>
  );
}
