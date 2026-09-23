-- V7: Таблица billing_attempts - audit log попыток списания по подпискам
CREATE TABLE billing_attempts
(
    id                       BIGSERIAL PRIMARY KEY,
    subscription_id          BIGINT      NOT NULL,
    payment_id               BIGINT,
    stripe_payment_intent_id VARCHAR(100),
    attempt_number           INT         NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message            VARCHAR(500),
    scheduled_at             TIMESTAMP   NOT NULL,
    executed_at              TIMESTAMP,
    created_at               TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_attempts_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
);

COMMENT ON TABLE billing_attempts IS 'Audit log попыток списания по подпискам; каждая запись - одна попытка создать PaymentIntent через Stripe';
COMMENT ON COLUMN billing_attempts.subscription_id IS 'Подписка по которой выполняется попытка';
COMMENT ON COLUMN billing_attempts.payment_id IS 'Payment созданный в рамках попытки; NULL если Stripe вернул ошибку до создания PaymentIntent';
COMMENT ON COLUMN billing_attempts.stripe_payment_intent_id IS 'Stripe PaymentIntent ID (pi_xxx); дублируется для поиска без JOIN на payments';
COMMENT ON COLUMN billing_attempts.attempt_number IS 'Порядковый номер попытки в текущем billing цикле (1, 2, 3...)';
COMMENT ON COLUMN billing_attempts.status IS 'Статус: PENDING - ждём webhook; SUCCEEDED - payment_intent.succeeded; FAILED - payment_intent.payment_failed';
COMMENT ON COLUMN billing_attempts.error_message IS 'Сообщение об ошибке от Stripe; NULL при успешном списании';
COMMENT ON COLUMN billing_attempts.scheduled_at IS 'Когда billing job запланировал попытку';
COMMENT ON COLUMN billing_attempts.executed_at IS 'Когда billing job выполнил запрос к Stripe';
COMMENT ON COLUMN billing_attempts.created_at IS 'Дата и время создания записи';

-- Для поиска истории попыток по подписке
CREATE INDEX idx_billing_attempts_subscription_id ON billing_attempts (subscription_id);

-- Для поиска зависших PENDING попыток
CREATE INDEX idx_billing_attempts_status ON billing_attempts (status);