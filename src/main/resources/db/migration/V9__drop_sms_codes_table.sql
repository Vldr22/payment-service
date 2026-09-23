-- V9: Удаление устаревшей таблицы sms_codes и обновление комментария subscription_status

DROP TABLE IF EXISTS sms_codes;

COMMENT ON COLUMN subscriptions.subscription_status IS
    'ACTIVE; PAST_DUE - идут retry; PROCESSING - ждём webhook; SUSPENDED - retry исчерпаны; CANCELLED';