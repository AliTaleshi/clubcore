import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Loading } from './components/common';
import { RequireAuth } from './auth/RequireAuth';
import { homeFor, useAuth } from './auth/AuthContext';

const LoginPage = lazy(() => import('./pages/common/LoginPage'));
const RegisterPage = lazy(() => import('./pages/common/RegisterPage'));
const PaymentResultPage = lazy(() => import('./pages/common/PaymentResultPage'));
const MockGatewayPage = lazy(() => import('./pages/common/MockGatewayPage'));
const NotificationsPage = lazy(() => import('./pages/common/NotificationsPage'));
const AccountPage = lazy(() => import('./pages/common/AccountPage'));
const AssistantPage = lazy(() => import('./pages/common/AssistantPage'));
const NotFoundPage = lazy(() => import('./pages/common/NotFoundPage'));
const DashboardPage = lazy(() => import('./pages/staff/DashboardPage'));
const MembersPage = lazy(() => import('./pages/staff/MembersPage'));
const MemberDetailPage = lazy(() => import('./pages/staff/MemberDetailPage'));
const PlansPage = lazy(() => import('./pages/staff/PlansPage'));
const AttendancePage = lazy(() => import('./pages/staff/AttendancePage'));
const KioskPage = lazy(() => import('./pages/staff/KioskPage'));
const InvoicesPage = lazy(() => import('./pages/staff/InvoicesPage'));
const AccountingPage = lazy(() => import('./pages/staff/AccountingPage'));
const CrmPage = lazy(() => import('./pages/staff/CrmPage'));
const LoyaltyAdminPage = lazy(() => import('./pages/staff/LoyaltyAdminPage'));
const ChurnPage = lazy(() => import('./pages/staff/ChurnPage'));
const InsightsPage = lazy(() => import('./pages/staff/InsightsPage'));
const SettingsPage = lazy(() => import('./pages/staff/SettingsPage'));
const TraineesPage = lazy(() => import('./pages/coach/TraineesPage'));
const MemberHomePage = lazy(() => import('./pages/member/MemberHomePage'));
const BuyPlanPage = lazy(() => import('./pages/member/BuyPlanPage'));
const MyInvoicesPage = lazy(() => import('./pages/member/MyInvoicesPage'));
const MyLoyaltyPage = lazy(() => import('./pages/member/MyLoyaltyPage'));
const MyProgramsPage = lazy(() => import('./pages/member/MyProgramsPage'));
const MyAttendancePage = lazy(() => import('./pages/member/MyAttendancePage'));
const ProfilePage = lazy(() => import('./pages/member/ProfilePage'));

function Home() {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  return <Navigate to={user ? homeFor(user.role) : '/login'} replace />;
}

export default function App() {
  const location = useLocation();
  return (
    <ErrorBoundary resetKey={location.pathname}>
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/payment/result" element={<PaymentResultPage />} />
        <Route path="/mock-gateway" element={<MockGatewayPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route path="/assistant" element={<AssistantPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/account" element={<AccountPage />} />
            <Route element={<RequireAuth roles={['ADMIN', 'RECEPTIONIST', 'ACCOUNTANT']} />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/members" element={<MembersPage />} />
              <Route path="/invoices" element={<InvoicesPage />} />
            </Route>
            <Route element={<RequireAuth roles={['ADMIN', 'RECEPTIONIST', 'ACCOUNTANT', 'COACH']} />}>
              <Route path="/members/:id" element={<MemberDetailPage />} />
            </Route>
            <Route element={<RequireAuth roles={['ADMIN', 'RECEPTIONIST', 'COACH']} />}>
              <Route path="/attendance" element={<AttendancePage />} />
            </Route>
            <Route element={<RequireAuth roles={['ADMIN', 'RECEPTIONIST']} />}>
              <Route path="/plans" element={<PlansPage />} />
              <Route path="/kiosk" element={<KioskPage />} />
              <Route path="/crm" element={<CrmPage />} />
              <Route path="/loyalty" element={<LoyaltyAdminPage />} />
              <Route path="/churn" element={<ChurnPage />} />
            </Route>
            <Route element={<RequireAuth roles={['ADMIN', 'ACCOUNTANT']} />}>
              <Route path="/accounting" element={<AccountingPage />} />
              <Route path="/insights" element={<InsightsPage />} />
            </Route>
            <Route element={<RequireAuth roles={['ADMIN']} />}>
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
            <Route element={<RequireAuth roles={['COACH']} />}>
              <Route path="/coach" element={<TraineesPage />} />
            </Route>
            <Route element={<RequireAuth roles={['MEMBER']} />}>
              <Route path="/me" element={<MemberHomePage />} />
              <Route path="/me/buy" element={<BuyPlanPage />} />
              <Route path="/me/invoices" element={<MyInvoicesPage />} />
              <Route path="/me/loyalty" element={<MyLoyaltyPage />} />
              <Route path="/me/programs" element={<MyProgramsPage />} />
              <Route path="/me/attendance" element={<MyAttendancePage />} />
              <Route path="/me/profile" element={<ProfilePage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
    </ErrorBoundary>
  );
}
