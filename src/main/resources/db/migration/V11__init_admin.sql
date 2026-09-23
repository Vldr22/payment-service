-- V11: администратор по умолчанию, раньше создавался при старте приложения
-- Значения подставляются Flyway из переменных окружения, в репозиторий не попадают

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO staff (name, surname, midname, email, password, role, user_status,
                   password_change_required, created_at)
VALUES ('${admin_name}', '${admin_surname}', '${admin_midname}', '${admin_email}',
        crypt('${admin_password}', gen_salt('bf', 12)),
        'ROLE_ADMIN', 'ACTIVE', FALSE, NOW())
ON CONFLICT (email) DO NOTHING;
