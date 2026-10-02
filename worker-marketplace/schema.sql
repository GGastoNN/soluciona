PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS oauth_states (
  state TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sellers (
  uid TEXT PRIMARY KEY,
  mp_user_id TEXT NOT NULL,
  access_token_enc TEXT NOT NULL,
  refresh_token_enc TEXT,
  expires_at INTEGER,
  store_id TEXT,
  pos_id TEXT,
  external_pos_id TEXT,
  setup_error TEXT,
  connected_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
  id TEXT PRIMARY KEY,
  request_id TEXT NOT NULL UNIQUE,
  client_uid TEXT NOT NULL,
  professional_uid TEXT NOT NULL,
  mp_order_id TEXT NOT NULL UNIQUE,
  amount_cents INTEGER NOT NULL,
  marketplace_fee_cents INTEGER NOT NULL,
  commission_bps INTEGER NOT NULL,
  status TEXT NOT NULL,
  qr_data TEXT,
  idempotency_key TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payments_professional ON payments(professional_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_client ON payments(client_uid, created_at DESC);

CREATE TABLE IF NOT EXISTS referral_codes (
  uid TEXT PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  role TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS referrals (
  id TEXT PRIMARY KEY,
  referrer_uid TEXT NOT NULL,
  referred_uid TEXT NOT NULL UNIQUE,
  referrer_role TEXT NOT NULL,
  referred_role TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  qualified_at INTEGER,
  rewarded_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_referrals_referrer ON referrals(referrer_uid, created_at DESC);

CREATE TABLE IF NOT EXISTS user_benefits (
  uid TEXT PRIMARY KEY,
  priority_tokens INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS professional_benefits (
  uid TEXT PRIMARY KEY,
  discounted_jobs_remaining INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);


CREATE TABLE IF NOT EXISTS payment_requests (
  id TEXT PRIMARY KEY,
  request_id TEXT NOT NULL UNIQUE,
  client_uid TEXT NOT NULL,
  professional_uid TEXT NOT NULL,
  amount_cents INTEGER NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payment_requests_client
  ON payment_requests(client_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_requests_professional
  ON payment_requests(professional_uid, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_attempts (
  id TEXT PRIMARY KEY,
  payment_request_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  client_uid TEXT NOT NULL,
  professional_uid TEXT NOT NULL,
  method TEXT NOT NULL,
  mp_order_id TEXT NOT NULL UNIQUE,
  amount_cents INTEGER NOT NULL,
  marketplace_fee_cents INTEGER NOT NULL,
  commission_bps INTEGER NOT NULL,
  status TEXT NOT NULL,
  qr_data TEXT,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment_request
  ON payment_attempts(payment_request_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_order
  ON payment_attempts(mp_order_id);
