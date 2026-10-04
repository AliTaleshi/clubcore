# کلاب‌کور — ClubCore

سامانه فارسی مدیریت باشگاه ورزشی: ورود و خروج، عضویت، پرداخت اینترنتی، نقش‌ها و سطوح دسترسی، حسابداری،
CRM، باشگاه مشتریان و قابلیت‌های هوش مصنوعی.

A Persian (RTL, Jalali calendar, Toman) gym management platform built with **Spring Boot 4.1 / Java 21**,
**React 18 / MUI**, **PostgreSQL 16** and **Docker Compose**. See [docs/DESIGN.md](docs/DESIGN.md) for the
architecture, data model and business rules.

## Features

| Area | What you get |
|---|---|
| Entry / exit | Rotating signed QR code in the member panel (60 s validity), card / RFID (keyboard-wedge readers), manual by phone or membership number; kiosk page toggles check-in/check-out; live presence; session counting |
| Memberships | Time-based and session-based plans, renewal stacking, freeze/unfreeze with per-plan budget, nightly expiry and renewal reminders |
| Payments | Pluggable gateways **Zarinpal** (v4, sandbox), **Zibal** (test merchant) and a **mock** gateway; admin switches the active one at runtime; cash/POS at reception; idempotent callbacks |
| Roles | Admin, receptionist, accountant, coach, member — enforced on every endpoint and reflected in the UI |
| Accounting | Double-entry journal posted automatically from payments and expenses, manual entries, chart of accounts, trial balance, income statement, ledger, Jalali-month charts, sales by plan |
| CRM | Lead pipeline, activities timeline, follow-up reminders, lead → member conversion, member segments, SMS campaigns (Kavenegar or mock) |
| Loyalty | Points for visits, purchases and referrals; bronze/silver/gold tiers with automatic renewal discounts; rewards catalog, discount/gift codes, leaderboard |
| AI | Claude-powered chat assistant (role-aware, grounded in live gym data), workout-plan generator for coaches/members, churn-risk radar with explainable reasons, personalised retention SMS, business insights. Everything falls back to built-in local logic when no API key is set |

## Quick start (Docker Compose)

```bash
cp .env.example .env        # then set JWT_SECRET, ADMIN_PASSWORD, POSTGRES_PASSWORD
docker compose up -d --build
```

Open <http://localhost:3000> and sign in with `ADMIN_PHONE` / `ADMIN_PASSWORD` from `.env`.

With `SEED_DEMO=true` (only applied to an empty database) the system also creates demo plans, ~40 members with
realistic visit history and payments, leads, expenses, and these demo accounts:

| Role | Phone | Password |
|---|---|---|
| Receptionist | 09120000001 | Staff@12345 |
| Accountant | 09120000002 | Staff@12345 |
| Coach | 09120000003 | Staff@12345 |
| Member | 09121000001 | Member@12345 |

Other demo members have no password and can log in with an SMS code (with `SMS_PROVIDER=mock` the code is
printed in the backend log: `docker compose logs backend | grep "MOCK SMS"`).

### Configuration

All settings are environment variables (see [.env.example](.env.example)):

- **Payments** — `ZARINPAL_MERCHANT_ID`, `ZARINPAL_SANDBOX`, `ZIBAL_MERCHANT`. `PAYMENT_MOCK_ENABLED=true` adds the
  mock gateway, which approves payments without charging anyone — **set it to `false` in production** (the default
  when unset). `APP_PUBLIC_URL` must be the URL users
  reach the site on, because gateways redirect back to `APP_PUBLIC_URL/api/payments/callback/{gateway}`.
- **SMS** — `SMS_PROVIDER=kavenegar` with `KAVENEGAR_API_KEY` / `KAVENEGAR_SENDER`, or `mock`.
- **AI** — `ANTHROPIC_API_KEY` enables Claude (default model `claude-opus-5`, override with `AI_MODEL`).
  Requests opt into the API's server-side refusal fallback. Without a key, the app uses local fallbacks.

Gym name, loyalty rules and the active gateway are editable at runtime in **تنظیمات** (Settings).

## Development

```bash
# Database
docker run -d --name clubcore-devdb -p 5432:5432 \
  -e POSTGRES_DB=clubcore -e POSTGRES_USER=clubcore -e POSTGRES_PASSWORD=clubcore postgres:16-alpine

# Backend (http://localhost:8080, API docs at /api/docs)
cd backend && SEED_DEMO=true PAYMENT_MOCK_ENABLED=true APP_PUBLIC_URL=http://localhost:5173 ./mvnw spring-boot:run

# Frontend (http://localhost:5173, proxies /api to :8080)
cd frontend && npm install && npm run dev
```

## Tests

| Suite | Command | Notes |
|---|---|---|
| Backend unit (29) | `cd backend && ./mvnw test` | validators, churn model, QR tokens, accounting rules, Zarinpal/Zibal clients, gateway registry, loyalty tiers, AI fallbacks |
| Backend integration (55) | `cd backend && ./mvnw verify` | Testcontainers PostgreSQL 16 + MockMvc: auth/OTP/refresh, RBAC, memberships, attendance, payments, accounting, loyalty, CRM, AI (fake LLM), concurrency (parallel check-ins, payments, redemptions) |
| Frontend (25) | `cd frontend && npm test` | Vitest + Testing Library + MSW |
| End-to-end | see below | Playwright against the full Docker Compose stack |

End-to-end tests run against the running stack (`SEED_DEMO=true`):

```bash
docker compose up -d --build
cd e2e && docker run --rm --network host -u $(id -u):$(id -g) -e HOME=/tmp -v $PWD:/work -w /work \
  mcr.microsoft.com/playwright:v1.63.0-noble sh -c "npm ci && npx playwright test"
```

## Project layout

```
backend/    Spring Boot modular monolith (auth, member, plan, membership, attendance, billing,
            accounting, crm, loyalty, coaching, notification, ai, dashboard, setting)
frontend/   React + TypeScript + MUI (RTL) single-page app, served by nginx in production
e2e/        Playwright end-to-end tests
docs/       Design document
```
