-- ===================== auth / users =====================
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(11)  NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE otp_codes (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(11) NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts   INT         NOT NULL DEFAULT 0,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone ON otp_codes (phone, created_at DESC);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE
);

-- ===================== members / plans =====================
CREATE TABLE members (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL UNIQUE REFERENCES users (id),
    membership_no   VARCHAR(20) NOT NULL UNIQUE,
    card_no         VARCHAR(40) UNIQUE,
    national_code   VARCHAR(10),
    gender          VARCHAR(10),
    birth_date      DATE,
    address         VARCHAR(300),
    emergency_phone VARCHAR(11),
    coach_id        BIGINT REFERENCES users (id),
    referral_code   VARCHAR(12) NOT NULL UNIQUE,
    referred_by     BIGINT REFERENCES members (id),
    goal            VARCHAR(300),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE plans (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    duration_days   INT          NOT NULL CHECK (duration_days > 0),
    session_limit   INT CHECK (session_limit IS NULL OR session_limit > 0),
    price           BIGINT       NOT NULL CHECK (price >= 0),
    max_freeze_days INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===================== billing =====================
CREATE TABLE invoices (
    id         BIGSERIAL PRIMARY KEY,
    number     VARCHAR(20)  NOT NULL UNIQUE,
    member_id  BIGINT       NOT NULL REFERENCES members (id),
    title      VARCHAR(200) NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    amount     BIGINT       NOT NULL CHECK (amount >= 0),
    discount   BIGINT       NOT NULL DEFAULT 0,
    total      BIGINT       NOT NULL CHECK (total >= 0),
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at    TIMESTAMPTZ
);
CREATE INDEX idx_invoice_member ON invoices (member_id);

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT      NOT NULL REFERENCES invoices (id),
    amount      BIGINT      NOT NULL,
    method      VARCHAR(20) NOT NULL,
    gateway     VARCHAR(20),
    status      VARCHAR(20) NOT NULL,
    authority   VARCHAR(100) UNIQUE,
    ref_id      VARCHAR(100),
    card_pan    VARCHAR(30),
    recorded_by BIGINT REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at     TIMESTAMPTZ
);
CREATE INDEX idx_payment_invoice ON payments (invoice_id);

CREATE TABLE memberships (
    id               BIGSERIAL PRIMARY KEY,
    member_id        BIGINT      NOT NULL REFERENCES members (id),
    plan_id          BIGINT      NOT NULL REFERENCES plans (id),
    invoice_id       BIGINT REFERENCES invoices (id),
    start_date       DATE        NOT NULL,
    end_date         DATE        NOT NULL,
    sessions_total   INT,
    sessions_used    INT         NOT NULL DEFAULT 0,
    freeze_days_used INT         NOT NULL DEFAULT 0,
    frozen_since     DATE,
    status           VARCHAR(20) NOT NULL,
    price            BIGINT      NOT NULL,
    discount         BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_membership_member ON memberships (member_id, status);

-- ===================== attendance =====================
CREATE TABLE attendances (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT      NOT NULL REFERENCES members (id),
    membership_id BIGINT REFERENCES memberships (id),
    check_in_at   TIMESTAMPTZ NOT NULL,
    check_out_at  TIMESTAMPTZ,
    method        VARCHAR(10) NOT NULL,
    recorded_by   BIGINT REFERENCES users (id)
);
CREATE INDEX idx_att_member ON attendances (member_id, check_in_at DESC);
CREATE INDEX idx_att_open ON attendances (check_out_at) WHERE check_out_at IS NULL;

-- ===================== accounting =====================
CREATE TABLE accounts (
    id     BIGSERIAL PRIMARY KEY,
    code   VARCHAR(10)  NOT NULL UNIQUE,
    name   VARCHAR(100) NOT NULL,
    type   VARCHAR(20)  NOT NULL,
    system BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE journal_entries (
    id          BIGSERIAL PRIMARY KEY,
    entry_date  DATE         NOT NULL,
    description VARCHAR(300) NOT NULL,
    source_type VARCHAR(30),
    source_id   BIGINT,
    created_by  BIGINT REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_journal_date ON journal_entries (entry_date);

CREATE TABLE journal_lines (
    id         BIGSERIAL PRIMARY KEY,
    entry_id   BIGINT NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    debit      BIGINT NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit     BIGINT NOT NULL DEFAULT 0 CHECK (credit >= 0)
);
CREATE INDEX idx_jl_account ON journal_lines (account_id);

CREATE TABLE expenses (
    id                   BIGSERIAL PRIMARY KEY,
    account_id           BIGINT       NOT NULL REFERENCES accounts (id),
    paid_from_account_id BIGINT       NOT NULL REFERENCES accounts (id),
    amount               BIGINT       NOT NULL CHECK (amount > 0),
    expense_date         DATE         NOT NULL,
    description          VARCHAR(300) NOT NULL,
    journal_entry_id     BIGINT REFERENCES journal_entries (id),
    created_by           BIGINT REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===================== CRM =====================
CREATE TABLE leads (
    id                  BIGSERIAL PRIMARY KEY,
    full_name           VARCHAR(120) NOT NULL,
    phone               VARCHAR(11)  NOT NULL,
    source              VARCHAR(30),
    status              VARCHAR(20)  NOT NULL,
    interest            VARCHAR(200),
    assigned_to         BIGINT REFERENCES users (id),
    follow_up_date      DATE,
    notes               TEXT,
    converted_member_id BIGINT REFERENCES members (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE crm_activities (
    id         BIGSERIAL PRIMARY KEY,
    lead_id    BIGINT REFERENCES leads (id) ON DELETE CASCADE,
    member_id  BIGINT REFERENCES members (id),
    type       VARCHAR(20) NOT NULL,
    content    TEXT        NOT NULL,
    created_by BIGINT REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaigns (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(150) NOT NULL,
    segment    VARCHAR(30)  NOT NULL,
    message    TEXT         NOT NULL,
    sent_count INT          NOT NULL DEFAULT 0,
    created_by BIGINT REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ===================== loyalty =====================
CREATE TABLE loyalty_transactions (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES members (id),
    points     INT         NOT NULL,
    reason     VARCHAR(20) NOT NULL,
    reference  VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_loyalty_member ON loyalty_transactions (member_id);

CREATE TABLE rewards (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(300),
    points_cost INT          NOT NULL CHECK (points_cost > 0),
    type        VARCHAR(20)  NOT NULL,
    value       BIGINT       NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE redemptions (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES members (id),
    reward_id  BIGINT      NOT NULL REFERENCES rewards (id),
    code       VARCHAR(12) NOT NULL UNIQUE,
    status     VARCHAR(10) NOT NULL,
    invoice_id BIGINT REFERENCES invoices (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at    TIMESTAMPTZ
);

-- ===================== coaching / notifications / AI / settings =====================
CREATE TABLE workout_programs (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT       NOT NULL REFERENCES members (id),
    coach_id     BIGINT REFERENCES users (id),
    title        VARCHAR(150) NOT NULL,
    content      TEXT         NOT NULL,
    ai_generated BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(150) NOT NULL,
    body       TEXT         NOT NULL,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user ON notifications (user_id, read);

CREATE TABLE sms_logs (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(11) NOT NULL,
    message    TEXT        NOT NULL,
    provider   VARCHAR(20) NOT NULL,
    success    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_messages (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_user ON ai_messages (user_id, created_at);

CREATE TABLE settings (
    key   VARCHAR(60) PRIMARY KEY,
    value VARCHAR(500) NOT NULL
);

CREATE SEQUENCE membership_no_seq START 1001;
CREATE SEQUENCE invoice_no_seq START 100001;

-- ===================== reference data =====================
INSERT INTO accounts (code, name, type, system) VALUES
 ('1110', 'صندوق (نقد)', 'ASSET', TRUE),
 ('1120', 'بانک', 'ASSET', TRUE),
 ('1130', 'درگاه پرداخت اینترنتی', 'ASSET', TRUE),
 ('2100', 'پیش‌دریافت از اعضا', 'LIABILITY', TRUE),
 ('3100', 'سرمایه', 'EQUITY', TRUE),
 ('4100', 'درآمد شهریه عضویت', 'INCOME', TRUE),
 ('4900', 'سایر درآمدها', 'INCOME', TRUE),
 ('5100', 'هزینه حقوق و دستمزد', 'EXPENSE', TRUE),
 ('5200', 'هزینه اجاره', 'EXPENSE', TRUE),
 ('5300', 'هزینه آب، برق و گاز', 'EXPENSE', TRUE),
 ('5400', 'هزینه تعمیر و نگهداری تجهیزات', 'EXPENSE', TRUE),
 ('5500', 'هزینه تبلیغات و بازاریابی', 'EXPENSE', TRUE),
 ('5900', 'سایر هزینه‌ها', 'EXPENSE', TRUE);

INSERT INTO settings (key, value) VALUES
 ('gym.name', 'باشگاه ورزشی کلاب‌کور'),
 ('gym.phone', '021-00000000'),
 ('gym.address', 'تهران'),
 ('payment.activeGateway', 'MOCK'),
 ('loyalty.checkinPoints', '10'),
 ('loyalty.tomanPerPoint', '10000'),
 ('loyalty.referralPoints', '200'),
 ('loyalty.silverThreshold', '1000'),
 ('loyalty.goldThreshold', '3000'),
 ('loyalty.silverDiscountPercent', '5'),
 ('loyalty.goldDiscountPercent', '10');

INSERT INTO rewards (title, description, points_cost, type, value) VALUES
 ('۱۰٪ تخفیف تمدید', 'کد تخفیف ۱۰ درصدی برای خرید اشتراک بعدی', 500, 'DISCOUNT_PERCENT', 10),
 ('۲۰۰ هزار تومان تخفیف', 'کد تخفیف مبلغی روی خرید بعدی', 800, 'DISCOUNT_AMOUNT', 200000),
 ('بطری آب ورزشی', 'هدیه فیزیکی، تحویل در پذیرش', 300, 'GIFT', 0);
