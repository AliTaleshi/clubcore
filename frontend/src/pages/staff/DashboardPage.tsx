import { Link as RouterLink } from 'react-router-dom';
import { Button, Card, CardContent, Grid, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import HowToRegOutlined from '@mui/icons-material/HowToRegOutlined';
import PaymentsOutlined from '@mui/icons-material/PaymentsOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Dashboard } from '../../api/types';
import { ChartBox, Empty, Loading, PageHeader, StatCard } from '../../components/common';
import { faDigits, formatDate, formatMoney, formatNumber } from '../../utils/format';
import { format, parseISO } from 'date-fns-jalali';

function change(curr: number, prev: number): string | undefined {
  if (!prev) return undefined;
  const pct = Math.round(((curr - prev) / prev) * 100);
  return `${pct >= 0 ? '▲' : '▼'} ${faDigits(Math.abs(pct))}٪ نسبت به ۳۰ روز قبل`;
}

const shortDate = (iso: string) => faDigits(format(parseISO(iso), 'MM/dd'));
const compact = (n: number) => (n >= 1_000_000 ? `${faDigits(Math.round(n / 1_000_000))}م` : faDigits(n));

export default function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api.get<Dashboard>('/dashboard').then((r) => r.data),
    refetchInterval: 60_000,
  });
  if (isLoading || !data) return <Loading />;
  const k = data.kpis;
  return (
    <>
      <PageHeader title="داشبورد" subtitle="نمای کلی وضعیت باشگاه"
        actions={<Button variant="contained" component={RouterLink} to="/kiosk">کیوسک ورود</Button>} />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="اعضای فعال" value={formatNumber(k.activeMembers)} icon={<PeopleAltOutlined />}
          hint={`${faDigits(k.newMembersLast30)} عضو جدید در ۳۰ روز`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="حاضر در باشگاه" value={formatNumber(k.presentNow)} icon={<HowToRegOutlined />}
          hint={`${faDigits(k.todayVisits)} مراجعه امروز`} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="درآمد ۳۰ روز اخیر" value={formatMoney(k.revenueLast30)} icon={<PaymentsOutlined />}
          hint={change(k.revenueLast30, k.revenuePrev30)} /></Grid>
        <Grid size={{ xs: 6, md: 3 }}><StatCard label="ریسک بالای ریزش" value={formatNumber(k.highChurnRisk)} icon={<WarningAmberOutlined />}
          color="error.main" hint={`${faDigits(k.expiringIn7Days)} اشتراک رو به اتمام`} /></Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card><CardContent>
            <Typography fontWeight={700} gutterBottom>مراجعات ۳۰ روز اخیر</Typography>
            <ChartBox>
              <ResponsiveContainer>
                <AreaChart data={data.visits}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                  <XAxis dataKey="date" tickFormatter={shortDate} fontSize={11} />
                  <YAxis allowDecimals={false} tickFormatter={(v) => faDigits(v)} fontSize={11} width={30} />
                  <Tooltip labelFormatter={(l) => formatDate(String(l))} formatter={(v) => [faDigits(Number(v)), 'مراجعه']} />
                  <Area type="monotone" dataKey="count" stroke="#0f766e" fill="#0f766e" fillOpacity={0.2} />
                </AreaChart>
              </ResponsiveContainer>
            </ChartBox>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}><CardContent>
            <Typography fontWeight={700} gutterBottom>ساعات شلوغی</Typography>
            <ChartBox>
              <ResponsiveContainer>
                <BarChart data={data.hourly}>
                  <XAxis dataKey="hour" tickFormatter={(h) => faDigits(h)} fontSize={11} />
                  <YAxis hide />
                  <Tooltip labelFormatter={(h) => `ساعت ${faDigits(Number(h))}`} formatter={(v) => [faDigits(Number(v)), 'مراجعه']} />
                  <Bar dataKey="count" fill="#c2410c" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </ChartBox>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card><CardContent>
            <Typography fontWeight={700} gutterBottom>درآمد و هزینه روزانه (تومان)</Typography>
            <ChartBox>
              <ResponsiveContainer>
                <BarChart data={data.finance}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                  <XAxis dataKey="date" tickFormatter={shortDate} fontSize={11} />
                  <YAxis tickFormatter={compact} fontSize={11} width={40} />
                  <Tooltip labelFormatter={(l) => formatDate(String(l))} formatter={(v, n) => [formatMoney(Number(v)), n === 'income' ? 'درآمد' : 'هزینه']} />
                  <Legend formatter={(v) => (v === 'income' ? 'درآمد' : 'هزینه')} />
                  <Bar dataKey="income" fill="#0f766e" />
                  <Bar dataKey="expense" fill="#dc2626" />
                </BarChart>
              </ResponsiveContainer>
            </ChartBox>
          </CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}><CardContent>
            <Typography fontWeight={700} gutterBottom>فروش پلن‌ها (۳۰ روز)</Typography>
            {!data.salesByPlan.length ? <Empty /> : (
              <Table size="small">
                <TableHead><TableRow><TableCell>پلن</TableCell><TableCell>تعداد</TableCell><TableCell>مبلغ</TableCell></TableRow></TableHead>
                <TableBody>
                  {data.salesByPlan.map((s) => (
                    <TableRow key={s.plan}><TableCell>{s.plan}</TableCell><TableCell>{faDigits(s.count)}</TableCell><TableCell>{formatMoney(s.revenue)}</TableCell></TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent></Card>
        </Grid>
        <Grid size={12}>
          <Card><CardContent>
            <Typography fontWeight={700} gutterBottom>اشتراک‌هایی که تا ۷ روز آینده تمام می‌شوند</Typography>
            {!data.expiring.length ? <Empty /> : (
              <Table size="small">
                <TableHead><TableRow><TableCell>عضو</TableCell><TableCell>پلن</TableCell><TableCell>پایان</TableCell><TableCell /></TableRow></TableHead>
                <TableBody>
                  {data.expiring.map((m) => (
                    <TableRow key={m.id} hover>
                      <TableCell>{m.memberName}</TableCell><TableCell>{m.planName}</TableCell><TableCell>{formatDate(m.endDate)}</TableCell>
                      <TableCell><Button size="small" component={RouterLink} to={`/members/${m.memberId}`}>پرونده</Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent></Card>
        </Grid>
      </Grid>
    </>
  );
}
