-- V1: Базовые таблицы пользователей, платежей и возвратов

CREATE TABLE users
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    surname     VARCHAR(50) NOT NULL,
    midname     VARCHAR(50),
    user_status VARCHAR(20) NOT NULL,
    phone       VARCHAR(20) UNIQUE,
    email       VARCHAR(255) UNIQUE,
    password    VARCHAR(255),
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT chk_user_status CHECK (user_status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE TABLE registration_codes
(
    id         UUID PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    role       VARCHAR(20)  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL,
    expires_at TIMESTAMP    NOT NULL
);

CREATE TABLE sms_codes
(
    id         UUID PRIMARY KEY,
    code       VARCHAR(4)  NOT NULL,
    phone      VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT chk_sms_code_status CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED'))
);

CREATE TABLE card_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(100) NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_card_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_card_tokens_user_id ON card_tokens (user_id);

CREATE TABLE payments
(
    id                       BIGSERIAL PRIMARY KEY,
    stripe_payment_intent_id VARCHAR(100)   NOT NULL UNIQUE,
    amount                   DECIMAL(15, 2) NOT NULL,
    currency                 VARCHAR(10)    NOT NULL,
    status                   VARCHAR(50)    NOT NULL,
    description              VARCHAR(100),
    client_secret            VARCHAR(200),
    user_id                  BIGINT         NOT NULL,
    card_token_id            BIGINT,
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_card_token FOREIGN KEY (card_token_id) REFERENCES card_tokens (id) ON DELETE SET NULL,
    CONSTRAINT chk_payment_status CHECK (status IN
                                         ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELED'))
);

CREATE INDEX idx_payments_stripe_payment_intent_id ON payments (stripe_payment_intent_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);

CREATE TABLE subscriptions
(
    id                BIGSERIAL PRIMARY KEY,
    subscription_type VARCHAR(50) NOT NULL,
    start_date        TIMESTAMP   NOT NULL,
    end_date          TIMESTAMP   NOT NULL,
    active            BOOLEAN     NOT NULL,
    user_id           BIGINT      NOT NULL,
    last_payment_id   BIGINT,
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP,
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscriptions_last_payment FOREIGN KEY (last_payment_id) REFERENCES payments (id) ON DELETE SET NULL,
    CONSTRAINT chk_subscription_type CHECK (subscription_type IN ('DEFAULT'))
);

CREATE TABLE webhook_events
(
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(100) NOT NULL UNIQUE,
    event_type   VARCHAR(50)  NOT NULL,
    payload      TEXT         NOT NULL,
    processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    processed_at TIMESTAMP
);

CREATE INDEX idx_webhook_events_event_id ON webhook_events (event_id);

CREATE TABLE refunds
(
    id               BIGSERIAL PRIMARY KEY,
    stripe_refund_id VARCHAR(100)   NOT NULL UNIQUE,
    amount           DECIMAL(15, 2) NOT NULL,
    status           VARCHAR(50)    NOT NULL,
    reason           VARCHAR(255),
    payment_id       BIGINT         NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT chk_refund_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELED'))
);
