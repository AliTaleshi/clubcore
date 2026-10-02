import type { Role } from '../utils/labels';

export interface User {
  id: number;
  phone: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface Member {
  id: number;
  userId: number;
  fullName: string;
  phone: string;
  membershipNo: string;
  cardNo: string | null;
  nationalCode: string | null;
  gender: 'MALE' | 'FEMALE' | null;
  birthDate: string | null;
  address: string | null;
  emergencyPhone: string | null;
  coachId: number | null;
  coachName: string | null;
  referralCode: string;
  goal: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
}

export interface Plan {
  id: number;
  name: string;
  description: string | null;
  durationDays: number;
  sessionLimit: number | null;
  price: number;
  maxFreezeDays: number;
  active: boolean;
}

export interface Membership {
  id: number;
  memberId: number;
  memberName: string;
  planId: number;
  planName: string;
  startDate: string;
  endDate: string;
  sessionsTotal: number | null;
  sessionsUsed: number;
  sessionsRemaining: number | null;
  freezeDaysUsed: number;
  maxFreezeDays: number;
  frozenSince: string | null;
  status: string;
  price: number;
  discount: number;
  invoiceId: number | null;
  createdAt: string;
}

export interface LoyaltySummary {
  balance: number;
  lifetimePoints: number;
  tier: 'BRONZE' | 'SILVER' | 'GOLD';
  tierTitle: string;
  nextTier: string | null;
  pointsToNextTier: number;
  discountPercent: number;
  referralCode: string;
  referrals: number;
}

export interface MemberSummary {
  member: Member;
  currentMembership: Membership | null;
  entryBlockReason: string | null;
  loyalty: LoyaltySummary;
  visitsLast30: number;
  totalVisits: number;
  lastVisit: string | null;
  inside: boolean;
  unreadNotifications?: number;
}

export interface Invoice {
  id: number;
  number: string;
  memberId: number;
  memberName: string;
  memberPhone: string;
  title: string;
  kind: 'MEMBERSHIP' | 'OTHER';
  amount: number;
  discount: number;
  total: number;
  status: string;
  createdAt: string;
  paidAt: string | null;
}

export interface Payment {
  id: number;
  invoiceId: number;
  invoiceNumber: string;
  invoiceTitle: string;
  amount: number;
  method: string;
  gateway: string | null;
  status: string;
  refId: string | null;
  cardPan: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface Attendance {
  id: number;
  memberId: number;
  memberName: string;
  membershipNo: string;
  checkInAt: string;
  checkOutAt: string | null;
  method: string;
}

export interface ScanResult {
  action: 'CHECK_IN' | 'CHECK_OUT';
  attendance: Attendance;
  memberName: string;
  membershipNo: string;
  planName: string | null;
  endDate: string | null;
  sessionsRemaining: number | null;
  message: string;
}

export interface Quote {
  price: number;
  tierDiscountPercent: number;
  tierDiscount: number;
  codeDiscount: number;
  total: number;
}

export interface PurchaseResult {
  membership: Membership;
  invoiceId: number;
  total: number;
  paid: boolean;
}

export interface Account {
  id: number;
  code: string;
  name: string;
  type: string;
  system: boolean;
}

export interface JournalLine {
  accountId: number;
  accountCode: string;
  accountName: string;
  debit: number;
  credit: number;
}

export interface JournalEntry {
  id: number;
  entryDate: string;
  description: string;
  sourceType: string | null;
  sourceId: number | null;
  lines: JournalLine[];
  total: number;
}

export interface Expense {
  id: number;
  accountId: number;
  accountName: string;
  paidFromAccountId: number;
  paidFromName: string;
  amount: number;
  expenseDate: string;
  description: string;
}

export interface TrialRow {
  accountId: number;
  code: string;
  name: string;
  type: string;
  debit: number;
  credit: number;
  balance: number;
}

export interface IncomeStatement {
  from: string;
  to: string;
  income: TrialRow[];
  expenses: TrialRow[];
  totalIncome: number;
  totalExpense: number;
  netProfit: number;
}

export interface SeriesPoint {
  date: string;
  income: number;
  expense: number;
}

export interface LedgerRow {
  date: string;
  entryId: number;
  description: string;
  debit: number;
  credit: number;
  balance: number;
}

export interface Lead {
  id: number;
  fullName: string;
  phone: string;
  source: string | null;
  status: string;
  interest: string | null;
  assignedTo: number | null;
  followUpDate: string | null;
  notes: string | null;
  convertedMemberId: number | null;
  createdAt: string;
}

export interface CrmActivity {
  id: number;
  leadId: number | null;
  memberId: number | null;
  type: string;
  content: string;
  createdBy: number | null;
  createdAt: string;
}

export interface Campaign {
  id: number;
  title: string;
  segment: string;
  message: string;
  sentCount: number;
  createdAt: string;
}

export interface Reward {
  id: number;
  title: string;
  description: string | null;
  pointsCost: number;
  type: string;
  value: number;
  active: boolean;
}

export interface Redemption {
  id: number;
  memberId: number;
  reward: Reward;
  code: string;
  status: 'ISSUED' | 'USED';
  invoiceId: number | null;
  createdAt: string;
  usedAt: string | null;
}

export interface LoyaltyTx {
  id: number;
  points: number;
  reason: string;
  reference: string | null;
  createdAt: string;
}

export interface MemberRisk {
  memberId: number;
  fullName: string;
  phone: string;
  membershipNo: string;
  risk: number;
  level: 'LOW' | 'MEDIUM' | 'HIGH';
  reasons: string[];
  lastVisit: string | null;
  membershipEnd: string | null;
}

export interface Kpis {
  activeMembers: number;
  presentNow: number;
  todayVisits: number;
  visitsLast30: number;
  visitsPrev30: number;
  revenueToday: number;
  revenueLast30: number;
  revenuePrev30: number;
  expenseLast30: number;
  newMembersLast30: number;
  expiringIn7Days: number;
  unpaidInvoices: number;
  leadsDueToday: number;
  highChurnRisk: number;
  mediumChurnRisk: number;
}

export interface Dashboard {
  kpis: Kpis;
  visits: { date: string; count: number }[];
  hourly: { hour: number; count: number }[];
  finance: SeriesPoint[];
  expiring: Membership[];
  salesByPlan: { plan: string; count: number; revenue: number }[];
}

export interface AiReply {
  content: string;
  source: 'claude' | 'local';
}

export interface AiMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface WorkoutProgram {
  id: number;
  memberId: number;
  coachId: number | null;
  title: string;
  content: string;
  aiGenerated: boolean;
  createdAt: string;
}

export interface Notification {
  id: number;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface GatewayInfo {
  type: string;
  title: string;
  active: boolean;
}
