-- Dev-only: wipe per-user portfolio, trades/notifications, and related rows.
-- Does NOT touch nrs_market / market_price_history (run only against nrs_finance).
-- Safe to re-run: skips tables that do not exist (mixed migration states).
--
-- Usage (Docker, from repo root):
--   Get-Content -Raw scripts/dev-wipe-user-activity.sql | docker exec -i nrs-postgres psql -U "$env:POSTGRES_USER" -d nrs_finance -v ON_ERROR_STOP=1
-- Or: pwsh -File scripts/Run-DevWipe.ps1

BEGIN;

-- ---------------------------------------------------------------------------
-- Current finance model (post v22)
-- ---------------------------------------------------------------------------
DELETE FROM portfolio_value_snapshots;
DELETE FROM manual_portfolio_positions;
DELETE FROM user_starred_asset;

-- ---------------------------------------------------------------------------
-- Notifications (same DB as finance in docker-compose)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'nrs_notification' AND table_name = 'notifications'
  ) THEN
    EXECUTE 'DELETE FROM nrs_notification.notifications';
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'email_delivery_audit'
  ) THEN
    EXECUTE 'DELETE FROM public.email_delivery_audit';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Legacy / pre-v22-jury-cleanup operational tables (if still present)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF to_regclass('public.trade') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.trade';
  END IF;

  IF to_regclass('public.suspicious_events') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.suspicious_events';
  END IF;

  IF to_regclass('public.review_tasks') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.review_tasks';
  END IF;

  IF to_regclass('public.fund_requests') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.fund_requests';
  END IF;

  IF to_regclass('public.whale_history') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.whale_history';
  END IF;

  IF to_regclass('public.portfolio_assets') IS NOT NULL THEN
    EXECUTE 'DELETE FROM public.portfolio_assets';
  END IF;

  IF to_regclass('public.transactions') IS NOT NULL THEN
    EXECUTE 'UPDATE public.transactions SET reversed_transaction_id = NULL WHERE reversed_transaction_id IS NOT NULL';
    EXECUTE 'DELETE FROM public.transactions';
  END IF;

  IF to_regclass('public.balances') IS NOT NULL THEN
    EXECUTE 'UPDATE public.balances SET amount = 0';
  END IF;
END $$;

COMMIT;
