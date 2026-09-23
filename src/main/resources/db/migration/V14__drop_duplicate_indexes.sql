-- V14: Удаление индексов, дублирующих ограничение UNIQUE, которое Postgres индексирует само

DROP INDEX IF EXISTS idx_payments_stripe_payment_intent_id;

DROP INDEX IF EXISTS idx_webhook_events_event_id;
