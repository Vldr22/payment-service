-- V10: период списания как ключ идемпотентности попытки

ALTER TABLE billing_attempts
    ADD COLUMN billing_period TIMESTAMP;

UPDATE billing_attempts
SET billing_period = scheduled_at
WHERE billing_period IS NULL;

ALTER TABLE billing_attempts
    ALTER COLUMN billing_period SET NOT NULL;

COMMENT
ON COLUMN billing_attempts.billing_period IS 'Оплачиваемый период: next_billing_date подписки на момент запуска. Уникален в паре с subscription_id';

CREATE UNIQUE INDEX uq_billing_attempts_subscription_period
    ON billing_attempts (subscription_id, billing_period);
