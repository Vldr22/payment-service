-- V5: Комментарии к колонкам всех таблиц

-- users
COMMENT ON TABLE users IS 'Клиенты системы; логин по номеру телефона через SMS-код';
COMMENT ON COLUMN users.id IS 'Уникальный идентификатор клиента';
COMMENT ON COLUMN users.name IS 'Имя клиента';
COMMENT ON COLUMN users.surname IS 'Фамилия клиента';
COMMENT ON COLUMN users.midname IS 'Отчество клиента (необязательно)';
COMMENT ON COLUMN users.user_status IS 'Статус аккаунта: ACTIVE, INACTIVE, BLOCKED';
COMMENT ON COLUMN users.phone IS 'Номер телефона клиента, используется для входа через SMS-код';
COMMENT ON COLUMN users.created_at IS 'Дата и время создания аккаунта';

-- staff
COMMENT ON COLUMN staff.id IS 'Уникальный идентификатор сотрудника';
COMMENT ON COLUMN staff.name IS 'Имя сотрудника';
COMMENT ON COLUMN staff.surname IS 'Фамилия сотрудника';
COMMENT ON COLUMN staff.midname IS 'Отчество сотрудника (необязательно)';
COMMENT ON COLUMN staff.email IS 'Email сотрудника, используется для входа';
COMMENT ON COLUMN staff.password IS 'Хеш пароля (BCrypt)';
COMMENT ON COLUMN staff.user_status IS 'Статус аккаунта: ACTIVE, INACTIVE, BLOCKED';
COMMENT ON COLUMN staff.created_at IS 'Дата и время создания аккаунта';

-- payments
COMMENT ON TABLE payments IS 'Платежи клиентов через Stripe PaymentIntent';
COMMENT ON COLUMN payments.id IS 'Уникальный идентификатор платежа';
COMMENT ON COLUMN payments.stripe_payment_intent_id IS 'Stripe PaymentIntent ID (pi_xxx), основной идентификатор платежа в Stripe';
COMMENT ON COLUMN payments.amount IS 'Сумма платежа в основной валюте (не в центах)';
COMMENT ON COLUMN payments.currency IS 'Валюта платежа: USD, EUR и др.';
COMMENT ON COLUMN payments.status IS 'Статус платежа: PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELED, REFUNDED';
COMMENT ON COLUMN payments.description IS 'Описание платежа';
COMMENT ON COLUMN payments.client_secret IS 'Stripe client_secret для подтверждения платежа на фронтенде';
COMMENT ON COLUMN payments.user_id IS 'Клиент, совершивший платёж';
COMMENT ON COLUMN payments.created_at IS 'Дата и время создания платежа';
COMMENT ON COLUMN payments.updated_at IS 'Дата и время последнего обновления статуса';

-- refunds
COMMENT ON TABLE refunds IS 'Запросы на возврат платежей; двухэтапный flow: клиент создаёт запрос, сотрудник одобряет или отклоняет';
COMMENT ON COLUMN refunds.id IS 'Уникальный идентификатор возврата';
COMMENT ON COLUMN refunds.amount IS 'Сумма возврата';
COMMENT ON COLUMN refunds.payment_id IS 'Платёж, по которому запрошен возврат';
COMMENT ON COLUMN refunds.created_at IS 'Дата и время создания запроса на возврат';
COMMENT ON COLUMN refunds.updated_at IS 'Дата и время последнего обновления статуса';

-- subscriptions
COMMENT ON TABLE subscriptions IS 'Подписки клиентов; биллинг управляется custom billing engine поверх Stripe';
COMMENT ON COLUMN subscriptions.id IS 'Уникальный идентификатор подписки';
COMMENT ON COLUMN subscriptions.subscription_type IS 'Тип подписки: DEFAULT';
COMMENT ON COLUMN subscriptions.start_date IS 'Дата начала подписки';
COMMENT ON COLUMN subscriptions.end_date IS 'Дата окончания текущего периода подписки';
COMMENT ON COLUMN subscriptions.active IS 'Активна ли подписка';
COMMENT ON COLUMN subscriptions.user_id IS 'Владелец подписки';
COMMENT ON COLUMN subscriptions.last_payment_id IS 'Последний успешный платёж по подписке';
COMMENT ON COLUMN subscriptions.created_at IS 'Дата и время создания подписки';
COMMENT ON COLUMN subscriptions.updated_at IS 'Дата и время последнего обновления';

-- webhook_events
COMMENT ON TABLE webhook_events IS 'Входящие Stripe webhook события; идемпотентная обработка через event_id';
COMMENT ON COLUMN webhook_events.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN webhook_events.event_id IS 'Stripe Event ID (evt_xxx), используется для предотвращения повторной обработки';
COMMENT ON COLUMN webhook_events.event_type IS 'Тип события: payment_intent.succeeded, charge.refunded и др.';
COMMENT ON COLUMN webhook_events.payload IS 'Полный JSON payload от Stripe';
COMMENT ON COLUMN webhook_events.processed IS 'Обработано ли событие';
COMMENT ON COLUMN webhook_events.created_at IS 'Дата и время получения события';
COMMENT ON COLUMN webhook_events.processed_at IS 'Дата и время обработки события';

-- sms_codes
COMMENT ON TABLE sms_codes IS 'SMS коды для верификации клиентов при входе';
COMMENT ON COLUMN sms_codes.id IS 'Уникальный идентификатор';
COMMENT ON COLUMN sms_codes.code IS 'SMS код верификации';
COMMENT ON COLUMN sms_codes.phone IS 'Номер телефона получателя';
COMMENT ON COLUMN sms_codes.status IS 'Статус кода: PENDING, VERIFIED, EXPIRED';
COMMENT ON COLUMN sms_codes.expires_at IS 'Время истечения кода';
COMMENT ON COLUMN sms_codes.created_at IS 'Дата и время создания кода';
COMMENT ON COLUMN sms_codes.updated_at IS 'Дата и время последнего обновления';

-- registration_codes
COMMENT ON TABLE registration_codes IS 'Коды регистрации (устаревшая таблица, оставлена для истории миграций)';
COMMENT ON COLUMN registration_codes.id IS 'Уникальный идентификатор';
COMMENT ON COLUMN registration_codes.code IS 'Код регистрации';
COMMENT ON COLUMN registration_codes.email IS 'Email получателя';
COMMENT ON COLUMN registration_codes.role IS 'Роль регистрируемого пользователя';
COMMENT ON COLUMN registration_codes.used IS 'Использован ли код';
COMMENT ON COLUMN registration_codes.created_at IS 'Дата и время создания';
COMMENT ON COLUMN registration_codes.expires_at IS 'Дата и время истечения';

-- saved_cards
COMMENT ON COLUMN saved_cards.id IS 'Уникальный идентификатор сохранённой карты';
COMMENT ON COLUMN saved_cards.created_at IS 'Дата и время привязки карты';
