-- V3: Токенизация карт по PCI DSS, номера карт не хранятся

ALTER TABLE users
    ADD COLUMN stripe_customer_id VARCHAR(100) UNIQUE;
COMMENT ON COLUMN users.stripe_customer_id IS 'Stripe Customer ID (cus_xxx), создаётся при первой привязке карты пользователем';

CREATE TABLE saved_cards
(
    id                       BIGSERIAL PRIMARY KEY,
    stripe_payment_method_id VARCHAR(100) NOT NULL UNIQUE,
    last4                    VARCHAR(4)   NOT NULL,
    brand                    VARCHAR(20)  NOT NULL,
    exp_month                SMALLINT     NOT NULL,
    exp_year                 SMALLINT     NOT NULL,
    default_card             BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id                  BIGINT       NOT NULL,
    created_at               TIMESTAMP    NOT NULL,
    CONSTRAINT fk_saved_cards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_saved_cards_user_id ON saved_cards (user_id);

COMMENT ON TABLE saved_cards IS 'Сохранённые платёжные методы пользователей через Stripe PaymentMethod (PCI DSS compliant)';
COMMENT ON COLUMN saved_cards.stripe_payment_method_id IS 'Stripe PaymentMethod ID (pm_xxx), основной идентификатор для списаний';
COMMENT ON COLUMN saved_cards.last4 IS 'Последние 4 цифры карты для отображения пользователю';
COMMENT ON COLUMN saved_cards.brand IS 'Платёжная система: visa, mastercard, amex и др.';
COMMENT ON COLUMN saved_cards.exp_month IS 'Месяц истечения срока действия карты';
COMMENT ON COLUMN saved_cards.exp_year IS 'Год истечения срока действия карты';
COMMENT ON COLUMN saved_cards.default_card IS 'Основная карта пользователя, используется для автосписаний по подпискам';
COMMENT ON COLUMN saved_cards.user_id IS 'Владелец карты';

ALTER TABLE payments
    DROP CONSTRAINT fk_payments_card_token;

ALTER TABLE payments
    DROP COLUMN card_token_id;

ALTER TABLE payments
    ADD COLUMN saved_card_id BIGINT REFERENCES saved_cards (id) ON DELETE SET NULL;

COMMENT ON COLUMN payments.saved_card_id IS 'Сохранённая карта, которой совершён платёж; NULL если платёж через разовый метод или карта удалена';

ALTER TABLE subscriptions
    ADD COLUMN saved_card_id BIGINT REFERENCES saved_cards (id) ON DELETE SET NULL;

COMMENT ON COLUMN subscriptions.saved_card_id IS 'Карта для автосписания по подписке; при удалении карты требуется обновление платёжного метода';

DROP TABLE card_tokens;
