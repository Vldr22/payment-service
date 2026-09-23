-- V4: Разделение таблицы users на users (клиенты) и staff (сотрудники/админы)
-- Клиенты логинятся по телефону, staff (сотрудники) - по email + password

-- 1. Создание таблицы staff
CREATE TABLE staff
(
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(50)  NOT NULL,
    surname                  VARCHAR(50)  NOT NULL,
    midname                  VARCHAR(50),
    email                    VARCHAR(255) NOT NULL UNIQUE,
    password                 VARCHAR(255) NOT NULL,
    role                     VARCHAR(20)  NOT NULL,
    user_status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    password_change_required BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP    NOT NULL,
    CONSTRAINT chk_staff_status CHECK (user_status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT chk_staff_role CHECK (role IN ('ROLE_EMPLOYEE', 'ROLE_ADMIN'))
);

COMMENT ON TABLE staff IS 'Внутренние пользователи системы: сотрудники поддержки и администраторы; логин по email + password';
COMMENT ON COLUMN staff.password_change_required IS 'TRUE при создании админом - сотрудник обязан сменить временный пароль при первом входе';
COMMENT ON COLUMN staff.role IS 'Роль: ROLE_EMPLOYEE (поддержка), ROLE_ADMIN (администратор)';

-- 2. Перенос сотрудников и админов из users в employees
INSERT INTO staff (id, name, surname, midname, email, password, role, user_status, password_change_required, created_at)
SELECT id,
       name,
       surname,
       midname,
       email,
       password,
       role,
       user_status,
       FALSE,
       created_at
FROM users
WHERE role IN ('ROLE_EMPLOYEE', 'ROLE_ADMIN');

COMMENT ON TABLE staff IS 'Данные перенесены из таблицы users (role IN ROLE_EMPLOYEE, ROLE_ADMIN)';

-- 3. Обновление FK в refunds.reviewed_by: users -> staff
ALTER TABLE refunds
    DROP CONSTRAINT IF EXISTS refunds_reviewed_by_fkey;

ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES staff (id) ON DELETE SET NULL;

COMMENT ON COLUMN refunds.reviewed_by IS 'Сотрудник, одобривший или отклонивший возврат (ссылка на staff)';

-- 4. Синхронизация последовательности staff_id_seq
SELECT setval('staff_id_seq', (SELECT MAX(id) FROM staff));

-- 5. Удаление сотрудников из таблицы users
DELETE
FROM users
WHERE role IN ('ROLE_EMPLOYEE', 'ROLE_ADMIN');

-- 6. Очистка users от employee-специфичных колонок
ALTER TABLE users
    DROP COLUMN email;
ALTER TABLE users
    DROP COLUMN password;

COMMENT ON TABLE users IS 'Клиенты системы; логин по номеру телефона через SMS-код';
COMMENT ON COLUMN users.role IS 'Всегда ROLE_USER для клиентов';
COMMENT ON COLUMN users.stripe_customer_id IS 'Stripe Customer ID (cus_xxx), создаётся при первой привязке карты';
