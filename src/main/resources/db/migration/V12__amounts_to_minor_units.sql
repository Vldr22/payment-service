-- V12: суммы хранятся целым числом в минорных единицах

ALTER TABLE payments
    ALTER COLUMN amount TYPE BIGINT USING amount::BIGINT;

ALTER TABLE refunds
    ALTER COLUMN amount TYPE BIGINT USING amount::BIGINT;

ALTER TABLE subscriptions
    ALTER COLUMN amount DROP DEFAULT;

ALTER TABLE subscriptions
    ALTER COLUMN amount TYPE BIGINT USING amount::BIGINT;

ALTER TABLE subscriptions
    ALTER COLUMN amount SET DEFAULT 0;

COMMENT ON COLUMN payments.amount IS 'Сумма платежа в минорных единицах валюты: 999 это 9.99';
COMMENT ON COLUMN refunds.amount IS 'Сумма возврата в минорных единицах валюты';
COMMENT ON COLUMN subscriptions.amount IS 'Стоимость периода в минорных единицах валюты';
