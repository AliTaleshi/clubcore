-- Database-level guards against races that application locks already prevent, plus missing indexes.

-- Close duplicate open visits left by earlier races (keep the newest) before enforcing uniqueness.
UPDATE attendances a SET check_out_at = a.check_in_at
WHERE a.check_out_at IS NULL
  AND EXISTS (SELECT 1 FROM attendances b WHERE b.member_id = a.member_id AND b.check_out_at IS NULL AND b.id > a.id);

-- Cancel duplicate pending memberships (keep the newest).
UPDATE memberships m SET status = 'CANCELLED'
WHERE m.status = 'PENDING_PAYMENT'
  AND EXISTS (SELECT 1 FROM memberships n WHERE n.member_id = m.member_id AND n.status = 'PENDING_PAYMENT' AND n.id > m.id);

-- A member can have at most one open visit (no double check-in).
CREATE UNIQUE INDEX ux_attendance_open_member ON attendances (member_id) WHERE check_out_at IS NULL;

-- At most one membership awaiting payment per member.
CREATE UNIQUE INDEX ux_membership_pending_member ON memberships (member_id) WHERE status = 'PENDING_PAYMENT';

-- Remove award points that earlier races granted more than once (keep the first award).
DELETE FROM loyalty_transactions a
USING loyalty_transactions b
WHERE a.reason IN ('CHECKIN', 'PURCHASE', 'REFERRAL')
  AND a.member_id = b.member_id AND a.reason = b.reason AND a.reference = b.reference
  AND a.id > b.id;

-- Idempotent loyalty awards (one check-in award per day, one purchase award per invoice, one referral bonus per referee).
CREATE UNIQUE INDEX ux_loyalty_award ON loyalty_transactions (member_id, reason, reference)
    WHERE reason IN ('CHECKIN', 'PURCHASE', 'REFERRAL');

-- One journal entry per automatically posted source document.
CREATE UNIQUE INDEX ux_journal_source ON journal_entries (source_type, source_id)
    WHERE source_type IN ('PAYMENT', 'EXPENSE');

CREATE INDEX idx_membership_invoice ON memberships (invoice_id);
CREATE INDEX idx_redemption_invoice ON redemptions (invoice_id);
CREATE INDEX idx_redemption_member ON redemptions (member_id);
CREATE INDEX idx_member_coach ON members (coach_id);
CREATE INDEX idx_invoice_status ON invoices (status, paid_at);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expiry ON refresh_tokens (expires_at);
CREATE INDEX idx_otp_expiry ON otp_codes (expires_at);
CREATE INDEX idx_lead_status ON leads (status, follow_up_date);
CREATE INDEX idx_crm_activity_member ON crm_activities (member_id);
CREATE INDEX idx_crm_activity_lead ON crm_activities (lead_id);
CREATE INDEX idx_program_member ON workout_programs (member_id);
