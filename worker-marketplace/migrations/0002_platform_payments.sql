PRAGMA foreign_keys = ON;
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
