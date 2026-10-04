import { Alert, Box, Button, Card, CardContent, Chip, Grid, IconButton, List, ListItem, ListItemText, Stack, Tooltip,
  Typography } from '@mui/material';
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { LoyaltySummary, LoyaltyTx, Redemption, Reward } from '../../api/types';
import { Loading, PageHeader, StatCard } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDateTime, formatMoney, formatNumber } from '../../utils/format';
import { loyaltyReason, rewardType, tierColors } from '../../utils/labels';

interface LoyaltyData {
  summary: LoyaltySummary;
  history: LoyaltyTx[];
  redemptions: Redemption[];
}

export function rewardValue(r: Reward): string {
  if (r.type === 'DISCOUNT_PERCENT') return `${faDigits(r.value)}٪ تخفیف`;
  if (r.type === 'DISCOUNT_AMOUNT') return `${formatMoney(r.value)} تخفیف`;
  return 'هدیه';
}

export default function MyLoyaltyPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data, isLoading } = useQuery({
    queryKey: ['my-loyalty'],
    queryFn: () => api.get<LoyaltyData>('/loyalty/me').then((r) => r.data),
  });
  const { data: rewards } = useQuery({
    queryKey: ['rewards'],
    queryFn: () => api.get<Reward[]>('/loyalty/rewards').then((r) => r.data),
  });
  const redeem = useMutation({
    mutationFn: (rewardId: number) => api.post<Redemption>('/loyalty/redeem', { rewardId }).then((r) => r.data),
    onSuccess: (r) => {
      notify(`جایزه دریافت شد. کد شما: ${r.code}`);
      qc.invalidateQueries({ queryKey: ['my-loyalty'] });
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  });

  if (isLoading || !data) return <Loading />;
  const s = data.summary;
  const inviteLink = `${window.location.origin}/register?ref=${s.referralCode}`;

  return (
    <>
      <PageHeader title="باشگاه مشتریان" subtitle="با حضور، خرید و معرفی دوستان امتیاز بگیرید و جایزه دریافت کنید" />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="امتیاز قابل استفاده" value={formatNumber(s.balance)} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="مجموع امتیاز کسب‌شده" value={formatNumber(s.lifetimePoints)} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard label="سطح" value={<Chip label={s.tierTitle} sx={{ bgcolor: tierColors[s.tier], color: '#fff', fontSize: 16 }} />}
            hint={s.discountPercent ? `${faDigits(s.discountPercent)}٪ تخفیف تمدید` : undefined} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="دوستان معرفی‌شده" value={faDigits(s.referrals)} /></Grid>
      </Grid>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Typography fontWeight={700} gutterBottom>دوستانتان را دعوت کنید</Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            با اولین خرید هر دوستی که با کد شما ثبت‌نام کند، امتیاز هدیه می‌گیرید.
          </Typography>
          <Stack direction="row" alignItems="center" spacing={1}>
            <Typography sx={{ fontFamily: 'monospace', fontSize: 20, letterSpacing: 2 }} dir="ltr">{s.referralCode}</Typography>
            <Tooltip title="کپی لینک دعوت">
              <IconButton onClick={() => {
                // The Clipboard API only exists on HTTPS/localhost; show the link otherwise.
                if (!navigator.clipboard) return notify(`لینک دعوت: ${inviteLink}`, 'info');
                navigator.clipboard.writeText(inviteLink).then(() => notify('لینک دعوت کپی شد'),
                  () => notify(`لینک دعوت: ${inviteLink}`, 'info'));
              }}
                aria-label="کپی لینک دعوت"><ContentCopyOutlined /></IconButton>
            </Tooltip>
          </Stack>
        </CardContent>
      </Card>

      <Typography variant="h6" sx={{ mb: 1 }}>جوایز</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {rewards?.map((r) => (
          <Grid key={r.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography fontWeight={700}>{r.title}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ minHeight: 40 }}>{r.description}</Typography>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 1 }}>
                  <Chip size="small" label={`${formatNumber(r.pointsCost)} امتیاز`} />
                  <Button size="small" variant="contained" disabled={s.balance < r.pointsCost || redeem.isPending}
                    onClick={() => redeem.mutate(r.id)}>دریافت</Button>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography fontWeight={700}>کدهای جایزه من</Typography>
              {!data.redemptions.length ? <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>هنوز جایزه‌ای دریافت نکرده‌اید</Typography> : (
                <List dense>
                  {data.redemptions.map((r) => (
                    <ListItem key={r.id} divider secondaryAction={<Chip size="small" label={r.status === 'USED' ? 'استفاده‌شده' : r.invoiceId ? 'رزرو شده' : 'قابل استفاده'}
                      color={r.status === 'USED' ? 'default' : 'success'} />}>
                      <ListItemText primary={<Box component="span" dir="ltr" sx={{ fontFamily: 'monospace' }}>{r.code}</Box>}
                        secondary={`${r.reward.title} — ${rewardType[r.reward.type]}`} />
                    </ListItem>
                  ))}
                </List>
              )}
              <Alert severity="info" sx={{ mt: 1 }}>کدهای تخفیف را هنگام خرید اشتراک وارد کنید؛ هدایا را در پذیرش تحویل بگیرید.</Alert>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography fontWeight={700}>تاریخچه امتیازها</Typography>
              <List dense>
                {data.history.map((t) => (
                  <ListItem key={t.id} divider secondaryAction={
                    <Typography color={t.points > 0 ? 'success.main' : 'error.main'} fontWeight={700} dir="ltr">
                      {t.points > 0 ? '+' : ''}{faDigits(t.points)}
                    </Typography>}>
                    <ListItemText primary={loyaltyReason[t.reason] ?? t.reason} secondary={formatDateTime(t.createdAt)} />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
