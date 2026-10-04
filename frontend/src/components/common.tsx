import { useEffect, type ReactNode } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
  type ChipProps,
} from '@mui/material';
import InboxOutlined from '@mui/icons-material/InboxOutlined';
import ReactMarkdown from 'react-markdown';
import { useTheme } from '@mui/material/styles';
import { chartColors } from '../theme';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  useEffect(() => {
    document.title = `${title} | کلاب‌کور`;
  }, [title]);
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3 }} justifyContent="space-between"
      alignItems={{ xs: 'stretch', sm: 'center' }}>
      <Box>
        <Typography variant="h5" component="h1">{title}</Typography>
        {subtitle && <Typography color="text.secondary" variant="body2" sx={{ mt: 0.5 }}>{subtitle}</Typography>}
      </Box>
      {actions && <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>{actions}</Stack>}
    </Stack>
  );
}

export function StatCard({ label, value, hint, icon, color = 'primary.main' }: {
  label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; color?: string;
}) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" color="text.secondary">{label}</Typography>
            <Typography variant="h5" sx={{ mt: 1, fontWeight: 800 }}>{value}</Typography>
            {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
          </Box>
          {icon && <Box sx={{ color, opacity: 0.85, display: 'flex' }}>{icon}</Box>}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function StatusChip({ map, value, size = 'small' }: {
  map: Record<string, { label: string; color: ChipProps['color'] }>; value: string; size?: ChipProps['size'];
}) {
  const item = map[value] ?? { label: value, color: 'default' as const };
  return <Chip size={size} label={item.label} color={item.color} variant={item.color === 'default' ? 'outlined' : 'filled'} />;
}

export function Loading() {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }} role="progressbar" aria-label="در حال بارگذاری">
      <CircularProgress />
    </Box>
  );
}

export function Empty({ text = 'موردی برای نمایش وجود ندارد' }: { text?: string }) {
  return (
    <Stack alignItems="center" spacing={1} sx={{ py: 5, color: 'text.secondary' }}>
      <InboxOutlined fontSize="large" />
      <Typography variant="body2">{text}</Typography>
    </Stack>
  );
}

export function ConfirmDialog({ open, title, text, confirmText = 'تأیید', onConfirm, onClose, loading, color = 'primary' }: {
  open: boolean; title: string; text: ReactNode; confirmText?: string; onConfirm: () => void; onClose: () => void;
  loading?: boolean; color?: 'primary' | 'error' | 'warning';
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Typography>{text}</Typography></DialogContent>
      <DialogActions>
        <Button onClick={onClose}>انصراف</Button>
        <Button variant="contained" color={color} onClick={onConfirm} disabled={loading}>{confirmText}</Button>
      </DialogActions>
    </Dialog>
  );
}

export function Markdown({ children }: { children: string }) {
  return (
    <Box sx={{ '& h1,& h2,& h3': { fontSize: '1.1rem', mt: 2, mb: 1 }, '& p': { my: 1, lineHeight: 1.9 },
      '& ul,& ol': { pr: 3, pl: 0 }, '& li': { mb: 0.5, lineHeight: 1.8 }, '& table': { borderCollapse: 'collapse' },
      '& td,& th': { border: 1, borderColor: 'divider', p: 0.5 } }}>
      <ReactMarkdown>{children}</ReactMarkdown>
    </Box>
  );
}

export function useChartColors() {
  return chartColors(useTheme().palette.mode);
}

/** Recharts is laid out LTR so axes render correctly; labels stay Persian. */
export function ChartBox({ height = 280, children }: { height?: number; children: ReactNode }) {
  return <Box dir="ltr" sx={{ width: '100%', height }}>{children}</Box>;
}
