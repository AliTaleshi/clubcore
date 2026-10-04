# ClubCore — Gym Management Platform (باشگاه‌یار)

Persian (RTL, Jalali calendar, Toman) web application for running a gym:
entry/exit, memberships, online payments, role-based access, accounting,
CRM, loyalty and AI-assisted features.

## 1. Decisions

| Topic | Decision |
|---|---|
| Backend | Spring Boot 4.1 (Java 21), Spring Security (stateless JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | React 18 + TypeScript + Vite, MUI (RTL), React Query, React Router, Recharts |
| Locale | Persian UI, RTL, Jalali dates (date-fns-jalali), amounts in **Toman** (stored as `bigint`) |
| Font | Vazirmatn (self-hosted via `@fontsource`, no Google Fonts dependency) |
| Deployment | Docker Compose: `db` (postgres:16), `backend`, `frontend` (nginx serving SPA + reverse-proxying `/api`) |
| Roles | `ADMIN`, `RECEPTIONIST`, `ACCOUNTANT`, `COACH`, `MEMBER` |
| Login | Phone + password; optional SMS OTP login (pluggable: Kavenegar / mock) |
| Plans | Duration (days) + optional session limit; freezing with a max freeze-day budget |
| Entry/exit | Rotating signed QR code (member panel), card number / RFID (keyboard-wedge reader), manual by staff |
| Payments | Pluggable gateways: Zarinpal (v4, sandbox), Zibal (test merchant), Mock. Active gateway chosen by admin at runtime. Cash/POS recorded by staff |
| Loyalty | Points (check-in, purchase, referral) + tiers (bronze/silver/gold with discount) + rewards catalog |
| AI | Claude API (Messages API) for chat/text generation, local statistical model for churn scoring; everything degrades gracefully to a local fallback when no API key is configured or the API is unreachable |

## 2. Architecture

```
            ┌──────────────────────── docker compose ────────────────────────┐
 Browser ──►│ frontend (nginx :80)  ── /api/* ──► backend (Spring Boot :8080) │──► Claude API
            │   React SPA                           │                        │──► Zarinpal / Zibal
            │                                       ▼                        │──► Kavenegar (SMS)
            │                                 db (postgres:16)               │
            └────────────────────────────────────────────────────────────────┘
```

Backend is a modular monolith; each package is a bounded module with its own
entities, repositories, services, DTOs and controllers:

```
ir.clubcore
 ├─ common      errors (ProblemDetail), paging, Jalali helpers, base entity
 ├─ config      security, JWT, CORS, app properties
 ├─ auth        login, OTP, refresh, me, password change
 ├─ user        users & staff management
 ├─ member      member profiles, QR tokens, cards
 ├─ plan        membership plans
 ├─ membership  purchase, activation, freeze/unfreeze, cancel, expiry job
 ├─ attendance  check-in / check-out, live occupancy, history
 ├─ billing     invoices, payments, gateways (zarinpal, zibal, mock)
 ├─ accounting  chart of accounts, journal (double-entry), expenses, reports
 ├─ crm         leads, activities, follow-up tasks, SMS campaigns, segments
 ├─ loyalty     points ledger, tiers, rewards, redemptions, referral
 ├─ coaching    workout programs assigned by coaches
 ├─ notification in-app notifications + SMS provider abstraction
 ├─ ai          Claude client, chat assistant, churn model, insights, plan generator
 ├─ dashboard   KPIs per role
 └─ setting     key/value runtime settings (gym info, active gateway, loyalty rules)
```

## 3. Roles & access matrix

| Capability | ADMIN | RECEPTIONIST | ACCOUNTANT | COACH | MEMBER |
|---|:-:|:-:|:-:|:-:|:-:|
| Manage staff users & settings | ✔ | | | | |
| Manage plans | ✔ | | | | |
| Members (create/edit/search) | ✔ | ✔ | view | own trainees | own profile |
| Sell membership / record cash payment | ✔ | ✔ | ✔ | | |
| Online payment of own invoices | | | | | ✔ |
| Check-in / check-out, kiosk | ✔ | ✔ | | | own QR |
| Invoices & payments | ✔ | ✔ (view/create) | ✔ | | own |
| Accounting (journal, expenses, reports) | ✔ | | ✔ | | |
| CRM (leads, activities, campaigns) | ✔ | ✔ | | | |
| Loyalty admin (rewards, adjust points) | ✔ | ✔ (redeem) | | | own points / redeem |
| Workout programs | ✔ | | | ✔ (own trainees) | own |
| AI: business insights / churn | ✔ | ✔ (churn) | ✔ (finance) | | |
| AI: coach assistant | ✔ | | | ✔ | ✔ (fitness assistant) |

Enforced with `@PreAuthorize` on controllers plus ownership checks in services.
The frontend hides menu items/routes per role, but the backend is the source of truth.

## 4. Data model (main tables)

```
users(id, phone UQ, password_hash, full_name, role, active, created_at)
otp_codes(id, phone, code_hash, expires_at, attempts, used)
refresh_tokens(id, user_id, token_hash, expires_at, revoked)

members(id, user_id UQ→users, membership_no UQ, card_no UQ, national_code, gender,
        birth_date, address, emergency_phone, coach_id→users, referral_code UQ,
        referred_by→members, notes, created_at)
plans(id, name, description, duration_days, session_limit NULL, price, max_freeze_days, active)
memberships(id, member_id, plan_id, start_date, end_date, sessions_total NULL, sessions_used,
            freeze_days_used, frozen_since NULL, status[PENDING_PAYMENT|ACTIVE|FROZEN|EXPIRED|CANCELLED],
            price, discount, invoice_id, created_at)
attendances(id, member_id, membership_id, check_in_at, check_out_at NULL,
            method[QR|CARD|MANUAL], recorded_by→users)

invoices(id, number UQ, member_id, title, amount, discount, total,
         status[UNPAID|PAID|CANCELLED], kind[MEMBERSHIP|OTHER], created_at, paid_at)
payments(id, invoice_id, amount, method[ONLINE|CASH|POS|POINTS], gateway[ZARINPAL|ZIBAL|MOCK] NULL,
         status[PENDING|PAID|FAILED], authority, ref_id, card_pan, recorded_by, created_at, paid_at)

accounts(id, code UQ, name, type[ASSET|LIABILITY|EQUITY|INCOME|EXPENSE], system)
journal_entries(id, entry_date, description, source_type, source_id, created_by, created_at)
journal_lines(id, entry_id, account_id, debit, credit)
expenses(id, account_id, paid_from_account_id, amount, expense_date, description, journal_entry_id)

leads(id, full_name, phone, source, status[NEW|CONTACTED|TRIAL|CONVERTED|LOST],
      assigned_to, follow_up_date, notes, converted_member_id, created_at)
crm_activities(id, lead_id NULL, member_id NULL, type[CALL|SMS|VISIT|NOTE|EMAIL], content, created_by, created_at)
campaigns(id, title, segment, message, sent_count, created_by, created_at)

loyalty_transactions(id, member_id, points (+/-), reason[CHECKIN|PURCHASE|REFERRAL|REDEEM|ADJUST|BONUS],
                     reference, created_at)
rewards(id, title, description, points_cost, type[DISCOUNT_PERCENT|DISCOUNT_AMOUNT|FREE_SESSIONS|GIFT], value, active)
redemptions(id, member_id, reward_id, code UQ, status[ISSUED|USED], created_at, used_at)

workout_programs(id, member_id, coach_id, title, content, ai_generated, created_at)
notifications(id, user_id, title, body, read, created_at)
settings(key PK, value)
ai_conversations(id, user_id, role, content, created_at)
```

Points balance / lifetime points are derived from the ledger (sum), so the ledger
is the single source of truth.

## 5. Key business rules

**Membership**
- Purchase creates a `PENDING_PAYMENT` membership + `UNPAID` invoice. Paying the invoice
  (cash/POS by staff, or online by member) activates it. Start date = requested start
  or the day after the member's last active membership ends (renewal stacking).
- Price = plan price − tier discount − redeemed reward discount (never below 0).
- Freeze: only `ACTIVE`, already started and with sessions left; unfreezing extends `end_date` by frozen days;
  total frozen days ≤ `plan.max_freeze_days`.
- A session-based membership whose sessions are used up does not delay a renewal: it is expired on activation
  of the new one, which starts immediately.
- Nightly job marks memberships past `end_date` (or sessions exhausted) as `EXPIRED`,
  and sends reminders 3 days before expiry.

**Entry / exit**
- Check-in requires an `ACTIVE` membership covering today with sessions remaining;
  consumes one session for session-based plans.
- A member can't check in twice without checking out (member row lock + partial unique index); open sessions are
  auto-closed at midnight.
- QR token = HMAC-signed `{memberId, exp}` valid for 60 s; member panel refreshes it every 30 s
  (screenshots can't be reused). Card number lookup for RFID readers. Manual by phone/membership no.
- Each check-in earns loyalty points (once per day).

**Payments**
- `PaymentGateway` interface: `request(amount, description, mobile, callbackUrl)` →
  `{authority, redirectUrl}`; `verify(authority, amount)` → `{success, refId, cardPan}`.
- Online flow: `POST /api/payments/online {invoiceId}` → redirect to gateway → gateway calls
  back `GET /api/payments/callback/{gateway}` → backend verifies → 302 to
  `/payment/result?id=…` in the SPA. Verification is idempotent.
- Mock gateway redirects to an SPA page that simulates success/failure (dev & tests). It only exists when
  `PAYMENT_MOCK_ENABLED=true`; otherwise a stored `MOCK` setting falls back to Zarinpal.
- Callbacks lock the payment row, then the invoice row, so concurrent callbacks / cash payments settle once.
- Zibal amounts are converted Toman→Rial (×10); Zarinpal uses `currency=IRT`.

**Accounting** (double-entry, every entry balanced, posted automatically)
- Paid invoice: Dr Cash(1110) / Bank(1120) / Gateway clearing(1130) — Cr Membership revenue(4100) or Other revenue(4900).
- Points discount is recorded in the invoice discount (contra revenue not modelled).
- Expense: Dr expense account (5xxx) — Cr Cash/Bank.
- Manual journal entries by accountant (validated: Σdebit = Σcredit > 0).
- Reports: trial balance, income statement (date range), daily cash report, revenue by month,
  revenue by plan.

**CRM**
- Leads pipeline with statuses, assignment, follow-up dates; "due today" list.
- Activities timeline per lead/member. Convert lead → member (creates user+member).
- Segments: `ALL_ACTIVE`, `EXPIRING_SOON` (≤7 days), `EXPIRED_RECENTLY` (≤30 days),
  `INACTIVE` (no visit 14 days), `HIGH_CHURN_RISK`, `BIRTHDAY_THIS_WEEK`.
- SMS campaigns to a segment through the SMS provider (logged).

**Loyalty**
- Earn: check-in +10 (once/day), purchase +1 per 10,000 Toman paid, referral +200 to the
  referrer when the referred member's first invoice is paid. (All configurable in settings.)
- Tiers by lifetime points: Bronze 0 (0%), Silver 1,000 (5%), Gold 3,000 (10% discount on memberships).
- Redeem rewards from catalog → redemption code; staff applies code on a purchase.

**AI**
- `ChurnModel` (local, always available): logistic score from days since last visit,
  visit-frequency trend (last 14 vs previous 28 days), days to expiry, remaining sessions,
  tenure, renewals → risk 0–100 + human-readable Persian reasons.
- `AiAssistant` chat: role-aware system prompt (member: fitness/nutrition coach using the
  member's own stats; staff: business assistant given current KPIs). Uses Claude; local
  fallback answers with a canned message + stats.
- Workout program generator for coaches/members (Claude, fallback: template by goal/level).
- CRM: personalized retention SMS text for at-risk members (Claude, fallback template).
- Business insights for admin/accountant: KPI summary + recommendations (Claude, fallback rule-based).

## 6. API overview (all under `/api`, JSON, Persian error messages via ProblemDetail)

```
POST /auth/login | /auth/otp/request | /auth/otp/verify | /auth/refresh | /auth/logout
GET  /auth/me      POST /auth/change-password     POST /auth/register (public member sign-up)
/users (staff CRUD, ADMIN)
/members  /members/{id}  /members/{id}/qr-card   /me/member  /me/qr
/plans
/memberships  /memberships/{id}/freeze|unfreeze|cancel   /me/memberships
/attendance/check-in {method, token|cardNo|memberId}  /attendance/{id}/check-out
/attendance/present  /attendance?from&to&memberId     /me/attendance
/invoices  /invoices/{id}/pay-cash   /me/invoices
/payments/online   /payments/callback/{gateway}   /payments/gateways (+ active switch)
/accounting/accounts | /journal | /expenses | /reports/{trial-balance|income-statement|daily|monthly-revenue|revenue-by-plan}
/crm/leads  /crm/leads/{id}/convert  /crm/activities  /crm/segments/{seg}/members  /crm/campaigns
/loyalty/me  /loyalty/rewards  /loyalty/redeem  /loyalty/members/{id}  /loyalty/adjust  /loyalty/redemptions/{code}
/coaching/programs  /coaching/trainees
/ai/chat  /ai/churn  /ai/workout-plan  /ai/retention-message/{memberId}  /ai/insights  /ai/status
/dashboard
/notifications  /settings
```

## 7. Frontend structure

```
src/
 ├─ api/          axios client (JWT + refresh interceptor), typed endpoint modules
 ├─ auth/         AuthContext, RequireRole guard
 ├─ components/   Layout (RTL drawer per role), DataTable, JalaliDatePicker, Money, StatCard …
 ├─ pages/
 │   ├─ auth/        Login (password / OTP), Register
 │   ├─ dashboard/   role dashboards (KPIs + charts)
 │   ├─ members/     list, detail (tabs: memberships, attendance, invoices, loyalty, CRM, programs)
 │   ├─ plans/
 │   ├─ attendance/  live presence, history, Kiosk (QR camera + card input + manual)
 │   ├─ billing/     invoices, payments, payment result, mock gateway
 │   ├─ accounting/  accounts, journal, expenses, reports
 │   ├─ crm/         leads board, campaigns, segments
 │   ├─ loyalty/     rewards admin, member points
 │   ├─ coaching/    trainees, programs
 │   ├─ ai/          assistant chat, churn radar, insights
 │   ├─ member/      member panel: my card (QR), my plan, buy plan, invoices, points, programs
 │   └─ settings/    gym settings, gateway selection, staff users
 └─ utils/        jalali/date formatting, money/number (Persian digits), validators (phone, national code)
```

## 8. Testing strategy

1. **Backend unit tests** (JUnit 5, Mockito): churn model, loyalty tier/points, membership
   date math & freeze, accounting balance validation, QR token signing, gateway clients
   (MockRestServiceServer), validators.
2. **Backend integration tests** (Testcontainers `postgres:16` + MockMvc): auth & RBAC,
   full purchase → online mock payment → activation → journal entries, check-in rules,
   CRM conversion, loyalty earn/redeem, reports.
3. **Frontend tests** (Vitest + Testing Library): utils, auth guard, login form, key pages
   with mocked API.
4. **End-to-end** (Playwright against `docker compose up`): admin creates plan & member →
   member logs in → buys plan with mock gateway → kiosk check-in → accountant sees revenue
   → CRM lead conversion → loyalty points visible.

## 9. Configuration (`.env`)

```
POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD
JWT_SECRET                      (≥ 32 bytes)
APP_PUBLIC_URL                  e.g. http://localhost:3000 (used for gateway callbacks)
ADMIN_PHONE / ADMIN_PASSWORD    bootstrap admin
SEED_DEMO=true|false            demo plans, members, attendance, payments
ZARINPAL_MERCHANT_ID / ZARINPAL_SANDBOX
ZIBAL_MERCHANT                  ("zibal" = test merchant)
SMS_PROVIDER=mock|kavenegar / KAVENEGAR_API_KEY / KAVENEGAR_SENDER
ANTHROPIC_API_KEY / AI_MODEL    (empty key ⇒ local fallback)
```
