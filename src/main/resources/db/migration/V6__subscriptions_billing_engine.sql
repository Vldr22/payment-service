-- V6: Подготовка таблицы subscriptions для custom billing engine
-- Заменяем boolean active на полноценный статус, добавляем поля для управления циклом списаний и retry логики

ALTER TABLE subscriptions
    DROP COLUMN active;

ALTER TABLE subscriptions
    ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
COMMENT ON COLUMN subscriptions.subscription_status IS 'Статус подписки: ACTIVE - активна; PAST_DUE - платёж просрочен, идут retry; SUSPENDED - retry исчерпаны; CANCELLED - отменена';

ALTER TABLE subscriptions
    ADD COLUMN next_billing_date TIMESTAMP NOT NULL DEFAULT NOW();
COMMENT ON COLUMN subscriptions.next_billing_date IS 'Дата следующего списания; billing job выбирает записи где next_billing_date <= NOW()';

ALTER TABLE subscriptions
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
COMMENT ON COLUMN subscriptions.retry_count IS 'Счётчик неудачных попыток подряд; сбрасывается в 0 при успешном списании; при достижении максимума статус переходит в SUSPENDED';

ALTER TABLE subscriptions
    ADD COLUMN amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00;
COMMENT ON COLUMN subscriptions.amount IS 'Сумма списания за период; определяется тарифом (BASIC / PREMIUM)';

ALTER TABLE subscriptions
    ADD COLUMN interval_days INT NOT NULL DEFAULT 30;
COMMENT ON COLUMN subscriptions.interval_days IS 'Период подписки в днях; после успешного списания next_billing_date сдвигается на interval_days вперёд';

-- Подписка без привязанной карты невозможна - автосписание требует сохранённого Stripe PaymentMethod
ALTER TABLE subscriptions
    ALTER COLUMN saved_card_id SET NOT NULL;
COMMENT ON COLUMN subscriptions.saved_card_id IS 'Карта для автосписания; NOT NULL - подписка без привязанной карты невозможна';

-- Billing job: WHERE next_billing_date <= NOW() AND subscription_status IN ('ACTIVE', 'PAST_DUE')
CREATE INDEX idx_subscriptions_billing
    ON subscriptions (next_billing_date, subscription_status);

ALTER TABLE subscriptions
    DROP CONSTRAINT chk_subscription_type;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscription_type CHECK (subscription_type IN ('BASIC', 'PREMIUM'));
COMMENT ON COLUMN subscriptions.subscription_type IS 'Тариф подписки: BASIC, PREMIUM';