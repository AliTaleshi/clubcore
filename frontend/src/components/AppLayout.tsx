import { useState, type ReactElement } from 'react';
import { Link as RouterLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AppBar, Avatar, Badge, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText,
  Menu, MenuItem, Toolbar, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import PeopleAltOutlined from '@mui/icons-material/PeopleAltOutlined';
import CardMembershipOutlined from '@mui/icons-material/CardMembershipOutlined';
import HowToRegOutlined from '@mui/icons-material/HowToRegOutlined';
import QrCodeScannerOutlined from '@mui/icons-material/QrCodeScannerOutlined';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import AccountBalanceOutlined from '@mui/icons-material/AccountBalanceOutlined';
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined';
import LoyaltyOutlined from '@mui/icons-material/LoyaltyOutlined';
import RadarOutlined from '@mui/icons-material/RadarOutlined';
import InsightsOutlined from '@mui/icons-material/InsightsOutlined';
import SmartToyOutlined from '@mui/icons-material/SmartToyOutlined';
import SettingsOutlined from '@mui/icons-material/SettingsOutlined';
import QrCode2Outlined from '@mui/icons-material/QrCode2Outlined';
import ShoppingCartOutlined from '@mui/icons-material/ShoppingCartOutlined';
import FitnessCenterOutlined from '@mui/icons-material/FitnessCenterOutlined';
import EventNoteOutlined from '@mui/icons-material/EventNoteOutlined';
import PersonOutlined from '@mui/icons-material/PersonOutlined';
import NotificationsOutlined from '@mui/icons-material/NotificationsOutlined';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';
import { roleLabels, type Role } from '../utils/labels';
import { faDigits } from '../utils/format';
import { useColorMode } from './ColorMode';

interface NavItem {
  to: string;
  label: string;
  icon: ReactElement;
  roles: Role[];
}

const STAFF: Role[] = ['ADMIN', 'RECEPTIONIST', 'ACCOUNTANT'];

export const NAV: NavItem[] = [
  { to: '/dashboard', label: 'داشبورد', icon: <DashboardOutlined />, roles: STAFF },
  { to: '/accounting', label: 'حسابداری', icon: <AccountBalanceOutlined />, roles: ['ADMIN', 'ACCOUNTANT'] },
  { to: '/members', label: 'اعضا', icon: <PeopleAltOutlined />, roles: STAFF },
  { to: '/coach', label: 'شاگردان من', icon: <GroupsOutlined />, roles: ['COACH'] },
  { to: '/plans', label: 'پلن‌های عضویت', icon: <CardMembershipOutlined />, roles: ['ADMIN', 'RECEPTIONIST'] },
  { to: '/attendance', label: 'حضور و غیاب', icon: <HowToRegOutlined />, roles: ['ADMIN', 'RECEPTIONIST', 'COACH'] },
  { to: '/kiosk', label: 'کیوسک ورود و خروج', icon: <QrCodeScannerOutlined />, roles: ['ADMIN', 'RECEPTIONIST'] },
  { to: '/invoices', label: 'فاکتورها', icon: <ReceiptLongOutlined />, roles: STAFF },
  { to: '/crm', label: 'مدیریت ارتباط با مشتری', icon: <SupportAgentOutlined />, roles: ['ADMIN', 'RECEPTIONIST'] },
  { to: '/loyalty', label: 'باشگاه مشتریان', icon: <LoyaltyOutlined />, roles: ['ADMIN', 'RECEPTIONIST'] },
  { to: '/churn', label: 'رادار ریزش (AI)', icon: <RadarOutlined />, roles: ['ADMIN', 'RECEPTIONIST'] },
  { to: '/insights', label: 'تحلیل هوشمند (AI)', icon: <InsightsOutlined />, roles: ['ADMIN', 'ACCOUNTANT'] },
  { to: '/me', label: 'کارت ورود من', icon: <QrCode2Outlined />, roles: ['MEMBER'] },
  { to: '/me/buy', label: 'خرید اشتراک', icon: <ShoppingCartOutlined />, roles: ['MEMBER'] },
  { to: '/me/invoices', label: 'فاکتورهای من', icon: <ReceiptLongOutlined />, roles: ['MEMBER'] },
  { to: '/me/loyalty', label: 'امتیازها و جوایز', icon: <LoyaltyOutlined />, roles: ['MEMBER'] },
  { to: '/me/programs', label: 'برنامه تمرینی', icon: <FitnessCenterOutlined />, roles: ['MEMBER'] },
  { to: '/me/attendance', label: 'سوابق حضور', icon: <EventNoteOutlined />, roles: ['MEMBER'] },
  { to: '/assistant', label: 'دستیار هوشمند', icon: <SmartToyOutlined />, roles: ['ADMIN', 'RECEPTIONIST', 'ACCOUNTANT', 'COACH', 'MEMBER'] },
  { to: '/me/profile', label: 'پروفایل', icon: <PersonOutlined />, roles: ['MEMBER'] },
  { to: '/settings', label: 'تنظیمات', icon: <SettingsOutlined />, roles: ['ADMIN'] },
];

const DRAWER = 264;

export function AppLayout() {
  const { user, logout } = useAuth();
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const { mode, toggle } = useColorMode();

  const { data: gym } = useQuery({
    queryKey: ['gym-info'],
    queryFn: () => api.get<Record<string, string>>('/public/gym-info').then((r) => r.data),
    staleTime: 5 * 60_000,
  });
  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api.get<{ unread: number }>('/notifications/unread-count').then((r) => r.data.unread),
    refetchInterval: 60_000,
  });

  if (!user) return null;
  const items = NAV.filter((n) => n.roles.includes(user.role));
  const isActive = (to: string) =>
    location.pathname === to || (to !== '/me' && location.pathname.startsWith(to + '/'));

  const drawer = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ gap: 1.5 }}>
        <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36 }}><FitnessCenterOutlined fontSize="small" /></Avatar>
        <Box sx={{ minWidth: 0 }}>
          <Typography fontWeight={800} noWrap>{gym?.name ?? 'کلاب‌کور'}</Typography>
          <Typography variant="caption" color="text.secondary">سامانه مدیریت باشگاه</Typography>
        </Box>
      </Toolbar>
      <Divider />
      <List sx={{ px: 1, flex: 1, overflowY: 'auto' }}>
        {items.map((n) => (
          <ListItemButton key={n.to} component={RouterLink} to={n.to} selected={isActive(n.to)}
            onClick={() => setOpen(false)} sx={{ borderRadius: 2, mb: 0.5 }}>
            <ListItemIcon sx={{ minWidth: 40 }}>{n.icon}</ListItemIcon>
            <ListItemText primary={n.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" color="inherit" elevation={0}
        sx={{ borderBottom: 1, borderColor: 'divider', width: { md: `calc(100% - ${DRAWER}px)` }, ml: { md: `${DRAWER}px` } }}>
        <Toolbar>
          {!desktop && (
            <IconButton edge="start" onClick={() => setOpen(true)} aria-label="منو"><MenuIcon /></IconButton>
          )}
          <Box sx={{ flex: 1 }} />
          <Tooltip title={mode === 'light' ? 'حالت تیره' : 'حالت روشن'}>
            <IconButton onClick={toggle} aria-label="تغییر حالت نمایش">
              {mode === 'light' ? <DarkModeOutlined /> : <LightModeOutlined />}
            </IconButton>
          </Tooltip>
          <Tooltip title="اعلان‌ها">
            <IconButton component={RouterLink} to="/notifications" aria-label="اعلان‌ها">
              <Badge badgeContent={unread ? faDigits(unread) : 0} color="error"><NotificationsOutlined /></Badge>
            </IconButton>
          </Tooltip>
          <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="حساب کاربری">
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'secondary.main', color: 'secondary.contrastText', fontSize: 14, fontWeight: 700 }}>{user.fullName.charAt(0)}</Avatar>
          </IconButton>
          <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography fontWeight={700}>{user.fullName}</Typography>
              <Typography variant="caption" color="text.secondary">{roleLabels[user.role]} — {faDigits(user.phone)}</Typography>
            </Box>
            <Divider />
            <MenuItem onClick={() => { setAnchor(null); navigate('/account'); }}>تغییر رمز عبور</MenuItem>
            <MenuItem onClick={async () => { setAnchor(null); await logout(); navigate('/login'); }}>خروج</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Box component="nav" sx={{ width: { md: DRAWER }, flexShrink: { md: 0 } }}>
        <Drawer variant={desktop ? 'permanent' : 'temporary'} anchor="left" open={desktop || open}
          onClose={() => setOpen(false)} ModalProps={{ keepMounted: true }}
          sx={{ '& .MuiDrawer-paper': { width: DRAWER, boxSizing: 'border-box' } }}>
          {drawer}
        </Drawer>
      </Box>
      <Box component="main" sx={{ flex: 1, minWidth: 0, p: { xs: 2, md: 3 }, mt: 8 }}>
        <Outlet />
      </Box>
    </Box>
  );
}
