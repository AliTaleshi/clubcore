import { Alert, Button, Card, CardContent, Grid, Typography } from '@mui/material';
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Kpis } from '../../api/types';
import { Loading, Markdown, PageHeader, StatCard } from '../../components/common';
import { faDigits, formatMoney, formatNumber } from '../../utils/format';

export default function InsightsPage() {
  const { data, isFetching, error, refetch } = useQuery({
    queryKey: ['insights'],
    queryFn: () => api.get<{ content: string; source: string; kpis: Kpis }>('/ai/insights').then((r) => r.data),
    staleTime: 10 * 60_000,
  });
  return (
    <>
      <PageHeader title="تحلیل هوشمند کسب‌وکار" subtitle="خلاصه وضعیت و پیشنهادهای عملی بر اساس داده‌های ۳۰ روز اخیر"
        actions={<Button variant="contained" startIcon={<AutoAwesomeOutlined />} onClick={() => refetch()} disabled={isFetching}>تحلیل مجدد</Button>} />
      {error && <Alert severity="error">{errorMessage(error)}</Alert>}
      {isFetching && !data ? <Loading /> : data && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Grid container spacing={2}>
              <Grid size={6}><StatCard label="اعضای فعال" value={formatNumber(data.kpis.activeMembers)} /></Grid>
              <Grid size={6}><StatCard label="اعضای جدید" value={formatNumber(data.kpis.newMembersLast30)} /></Grid>
              <Grid size={12}><StatCard label="درآمد ۳۰ روز" value={formatMoney(data.kpis.revenueLast30)} hint={`دوره قبل: ${formatMoney(data.kpis.revenuePrev30)}`} /></Grid>
              <Grid size={12}><StatCard label="هزینه ۳۰ روز" value={formatMoney(data.kpis.expenseLast30)} /></Grid>
              <Grid size={6}><StatCard label="مراجعات" value={formatNumber(data.kpis.visitsLast30)} hint={`قبل: ${faDigits(data.kpis.visitsPrev30)}`} /></Grid>
              <Grid size={6}><StatCard label="ریسک ریزش بالا" value={formatNumber(data.kpis.highChurnRisk)} /></Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card><CardContent>
              {isFetching && <Typography color="text.secondary" variant="body2">در حال تحلیل…</Typography>}
              {data.source === 'local' && <Alert severity="info" sx={{ mb: 2 }}>سرویس هوش مصنوعی در دسترس نیست؛ تحلیل بر اساس قواعد داخلی انجام شده است.</Alert>}
              <Markdown>{data.content}</Markdown>
            </CardContent></Card>
          </Grid>
        </Grid>
      )}
    </>
  );
}
