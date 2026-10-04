import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Card, CardContent, Chip, Grid, LinearProgress, Stack, Typography } from '@mui/material';
import { QRCodeSVG } from 'qrcode.react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { MemberSummary } from '../../api/types';
import { Loading, PageHeader, StatCard, StatusChip } from '../../components/common';
import { daysUntil, faDigits, formatDate, formatDateTime, formatNumber } from '../../utils/format';
import { membershipStatus, tierColors } from '../../utils/labels';

/** Member entry card: the QR rotates every 30 s and each code is valid for 60 s, so screenshots cannot be shared. */
function QrCard({ membershipNo }: { membershipNo: string }) {
  const { data, dataUpdatedAt, refetch } = useQuery({
    queryKey: ['my-qr'],
    queryFn: () => api.get<{ token: string; expiresAt: number }>('/me/qr').then((r) => r.data),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    // The code expires after 60 s, so never show a cached one when the member comes back to the tab.
    refetchOnWindowFocus: 'always',
    refetchOnMount: 'always',
    staleTime: 0,
  });
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const remaining = Math.max(0, 30 - Math.floor((now - dataUpdatedAt) / 1000));
  // Timers are throttled in background tabs; refresh as soon as the shown code is due.
  useEffect(() => {
    if (dataUpdatedAt && remaining === 0) refetch();
  }, [remaining, dataUpdatedAt, refetch]);
  return (
    <Card sx={{ textAlign: 'center' }}>
      <CardContent>
        <Typography fontWeight={700} gutterBottom>کارت ورود</Typography>
        <Box sx={{ bgcolor: '#fff', p: 2, borderRadius: 2, display: 'inline-block' }} data-testid="qr-box">
          {data ? <QRCodeSVG value={data.token} size={200} level="M" /> : <Box sx={{ width: 200, height: 200 }} />}
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          شماره عضویت: {faDigits(membershipNo)}
        </Typography>
        <LinearProgress variant="determinate" value={(remaining / 30) * 100} sx={{ mt: 1.5, borderRadius: 1 }} />
        <Typography variant="caption" color="text.secondary">
          کد هر ۳۰ ثانیه تازه می‌شود ({faDigits(remaining)})
        </Typography>
      </CardContent>
    </Card>
  );
}

export default function MemberHomePage() {
  const { data, isLoading } = useQuery({
    queryKey: ['me-member'],
    queryFn: () => api.get<MemberSummary>('/me/member').then((r) => r.data),
  });
  if (isLoading || !data) return <Loading />;
  const ms = data.currentMembership;
  const l = data.loyalty;
  const tierProgress = l.nextTier ? Math.min(100, (l.lifetimePoints / (l.lifetimePoints + l.pointsToNextTier)) * 100) : 100;

  return (
    <>
      <PageHeader title={`سلام ${data.member.fullName.split(' ')[0]}!`}
        subtitle={data.inside ? 'هم‌اکنون در باشگاه حضور دارید' : 'به پنل عضویت خوش آمدید'} />
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <QrCard membershipNo={data.member.membershipNo} />
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Stack spacing={2}>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                  <Typography fontWeight={700}>اشتراک فعلی</Typography>
                  {ms && <StatusChip map={membershipStatus} value={ms.status} />}
                </Stack>
                {ms ? (
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 4 }}><Typography color="text.secondary" variant="body2">پلن</Typography><Typography fontWeight={700}>{ms.planName}</Typography></Grid>
                    <Grid size={{ xs: 6, sm: 4 }}><Typography color="text.secondary" variant="body2">اعتبار تا</Typography><Typography fontWeight={700}>{formatDate(ms.endDate)}</Typography>
                      <Typography variant="caption" color={daysUntil(ms.endDate) <= 7 ? 'error' : 'text.secondary'}>{faDigits(Math.max(0, daysUntil(ms.endDate)))} روز مانده</Typography></Grid>
                    <Grid size={{ xs: 6, sm: 4 }}><Typography color="text.secondary" variant="body2">جلسات باقیمانده</Typography>
                      <Typography fontWeight={700}>{ms.sessionsRemaining === null ? 'نامحدود' : faDigits(ms.sessionsRemaining)}</Typography></Grid>
                  </Grid>
                ) : (
                  <Alert severity="warning" action={<Button component={RouterLink} to="/me/buy" color="inherit">خرید اشتراک</Button>}>
                    {data.entryBlockReason ?? 'اشتراک فعالی ندارید'}
                  </Alert>
                )}
                {ms && daysUntil(ms.endDate) <= 7 && (
                  <Button component={RouterLink} to="/me/buy" variant="outlined" sx={{ mt: 2 }}>تمدید اشتراک</Button>
                )}
              </CardContent>
            </Card>
            <Grid container spacing={2}>
              <Grid size={{ xs: 6, sm: 4 }}><StatCard label="حضور ۳۰ روز اخیر" value={faDigits(data.visitsLast30)} hint="جلسه" /></Grid>
              <Grid size={{ xs: 6, sm: 4 }}><StatCard label="کل جلسات" value={faDigits(data.totalVisits)} hint={data.lastVisit ? `آخرین: ${formatDateTime(data.lastVisit)}` : undefined} /></Grid>
              <Grid size={{ xs: 12, sm: 4 }}><StatCard label="امتیاز باشگاه مشتریان" value={formatNumber(l.balance)} hint={`سطح ${l.tierTitle}`} /></Grid>
            </Grid>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography fontWeight={700}>سطح عضویت</Typography>
                  <Chip label={l.tierTitle} sx={{ bgcolor: tierColors[l.tier], color: '#fff' }} />
                </Stack>
                <LinearProgress variant="determinate" value={tierProgress} sx={{ my: 1.5, height: 8, borderRadius: 4 }} />
                <Typography variant="body2" color="text.secondary">
                  {l.nextTier ? `${formatNumber(l.pointsToNextTier)} امتیاز تا سطح بعد` : 'شما در بالاترین سطح هستید'}
                  {l.discountPercent > 0 && ` — ${faDigits(l.discountPercent)}٪ تخفیف دائمی روی تمدید`}
                </Typography>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </>
  );
}
