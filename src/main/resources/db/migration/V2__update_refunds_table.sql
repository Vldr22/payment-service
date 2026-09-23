-- V2: Возврат с одобрением сотрудником

ALTER TABLE refunds
    ALTER COLUMN stripe_refund_id DROP NOT NULL;
COMMENT ON COLUMN refunds.stripe_refund_id IS 'Stripe refund ID, заполняется после одобрения возврата сотрудником';

ALTER TABLE refunds
    ALTER COLUMN reason TYPE VARCHAR(30);
COMMENT ON COLUMN refunds.reason IS 'Причина возврата: DUPLICATE, FRAUDULENT, REQUESTED_BY_CUSTOMER';

ALTER TABLE refunds
    ADD COLUMN user_id BIGINT REFERENCES users (id);
COMMENT ON COLUMN refunds.user_id IS 'Клиент, запросивший возврат';

ALTER TABLE refunds
    ADD COLUMN reviewed_by BIGINT REFERENCES users (id);
COMMENT ON COLUMN refunds.reviewed_by IS 'Сотрудник, одобривший или отклонивший возврат';

ALTER TABLE refunds
    DROP CONSTRAINT chk_refund_status;

ALTER TABLE refunds
    ADD CONSTRAINT chk_refund_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUCCEEDED', 'FAILED'));
COMMENT ON COLUMN refunds.status IS 'Статус возврата: PENDING -> APPROVED/REJECTED -> SUCCEEDED/FAILED';

ALTER TABLE payments
    DROP CONSTRAINT chk_payment_status;

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELED', 'REFUNDED'));

CREATE INDEX idx_refund_payment_id ON refunds (payment_id);
CREATE INDEX idx_refund_status ON refunds (status);
CREATE INDEX idx_refund_user_id ON refunds (user_id);
