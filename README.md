[![CI](https://github.com/Vldr22/payment-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Vldr22/payment-service/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Maven](https://img.shields.io/badge/Maven-3.9-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-003153)
![Redis](https://img.shields.io/badge/Redis-7-7B001C)
![Stripe](https://img.shields.io/badge/Stripe-API-635BFF)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203.0-85EA2D)
![Testcontainers](https://img.shields.io/badge/Testcontainers-WireMock-purple)
![JaCoCo](https://img.shields.io/badge/Coverage-JaCoCo-yellow)

# Payment Service

Платёжный сервис с интеграцией Stripe: разовые платежи, подписки с автоматическим списанием
и двухэтапный возврат средств.

## Ключевые особенности

**Аутентификация**
- Клиенты входят по SMS-коду, сотрудники по email и паролю
- JWT в HttpOnly cookie с `SameSite=Strict`, отзыв через blacklist в Redis
- Роли `ROLE_USER`, `ROLE_EMPLOYEE`, `ROLE_ADMIN`; администратор заводится миграцией

**Платежи**
- Приём через Stripe PaymentIntent API, подтверждение новой или сохранённой картой
- Номера карт не хранятся: токенизация через Stripe Customer и PaymentMethod

**Возвраты**
- Клиент создаёт заявку, сотрудник поддержки одобряет или отклоняет
- Возврат уходит в Stripe только после одобрения, финальный статус приходит webhook'ом

**Подписки и биллинг**
- Списание по расписанию через Spring Scheduler, без участия клиента
- Идемпотентный ключ из подписки и периода: сбой между списанием и записью не приводит
  к повторному списанию
- Статусная машина: `PAST_DUE` -> `PROCESSING` -> `ACTIVE` / `SUSPENDED` / `CANCELLED`,
  с ретраями и приостановкой после исчерпания

**Webhook**
- Финальный статус платежа приходит от Stripe асинхронно: webhook закрывает попытку
  списания и продлевает подписку
- Эндпоинт открыт для всех, аутентификация идёт подписью запроса
- Событие сохраняется по `event_id`, повторная доставка обработку не запускает

## Архитектура

- Слои: контроллер, фасад, сервис, репозиторий
- Обработчики webhook собираются в реестр по типу события (Strategy), новый тип добавляется
  отдельной реализацией
- Биллинг: планировщик передаёт подписку оркестратору, попытка списания уникальна
  в пределах периода на уровне ограничения БД
- Схема базы ведётся миграциями Flyway
- Единый формат ответа `CommonResponse` с `ProblemDetail`, ошибки собираются
  в `GlobalExceptionHandler`, security-ошибки отдельно в `SecurityExceptionHandler`
- Валидация входящих DTO аннотациями, настройки через `@ConfigurationProperties`

## API

**Аутентификация и пароли**

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/auth/register-client` | Регистрация клиента |
| POST | `/api/v1/auth/sms/send` | Отправка кода подтверждения |
| POST | `/api/v1/auth/sms/verify-client` | Вход клиента по коду |
| POST | `/api/v1/auth/staff/login` | Вход сотрудника |
| PATCH | `/api/v1/auth/staff/set-password` | Установка первичного пароля |
| POST | `/api/v1/auth/logout` | Выход, токен в blacklist |

**Клиент** (`ROLE_USER`)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/payments` | Создание платежа |
| POST | `/api/v1/payments/{id}/confirm` | Подтверждение новой картой |
| POST | `/api/v1/payments/{id}/confirm/saved-card` | Подтверждение сохранённой картой |
| GET | `/api/v1/payments/{id}` | Статус платежа |
| POST | `/api/v1/payments/{id}/refund` | Заявка на возврат |
| POST | `/api/v1/cards` | Привязка карты |
| GET | `/api/v1/cards` | Список карт |
| PATCH | `/api/v1/cards/{id}/default` | Карта по умолчанию |
| DELETE | `/api/v1/cards/{id}` | Удаление карты |
| POST | `/api/v1/subscriptions` | Оформление подписки |
| GET | `/api/v1/subscriptions` | Текущая подписка |
| PATCH | `/api/v1/subscriptions/cancel` | Отмена подписки |
| GET | `/api/v1/subscriptions/billing-history` | История списаний |

**Поддержка и администрирование**

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| GET | `/api/v1/support/refunds/pending` | `ROLE_EMPLOYEE` | Заявки на возврат |
| POST | `/api/v1/support/refunds/{id}/approve` | `ROLE_EMPLOYEE` | Одобрить возврат |
| POST | `/api/v1/support/refunds/{id}/reject` | `ROLE_EMPLOYEE` | Отклонить возврат |
| PATCH | `/api/v1/staff/password` | `ROLE_EMPLOYEE`, `ROLE_ADMIN` | Смена пароля сотрудника |
| POST | `/api/v1/admin/register-employee` | `ROLE_ADMIN` | Создание сотрудника |

**Webhook**

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/v1/webhooks/stripe` | Приём событий Stripe |

## Тестирование

- **Unit** - Mockito, запуск через Surefire
- **Интеграционные** - Testcontainers с PostgreSQL и WireMock вместо Stripe, запуск через Failsafe
- Тесты идут на реальной схеме, её применяет Flyway

```bash
./mvnw verify
```

Объединённый отчёт JaCoCo собирается в `target/site/jacoco-merged/`.

## Быстрый старт

```bash
git clone https://github.com/Vldr22/payment-service.git
cd payment-service
cp .env.example .env
docker compose -f docker-compose.dev.yml up --build
```

Ключи Stripe берутся из тестового режима. Администратор создаётся миграцией из переменных
`ADMIN_*`, пароль остаётся в `.env` и в репозиторий не попадает.

**Swagger UI:** http://localhost:8080/swagger-ui/index.html

Для webhook нужен локальный слушатель, его секрет идёт в `STRIPE_WEBHOOK_SECRET`:

```bash
stripe listen --all-snapshot --forward-to localhost:8080/api/v1/webhooks/stripe
```

## Roadmap

- [x] CI на GitHub Actions, защита main, сканирование секретов
- [x] Docker Compose
- [ ] Расширенное управление для администратора: фильтрация платежей, подписок, пользователей
- [ ] Email уведомления о статусе платежей и подписок
- [ ] Prometheus + Grafana мониторинг метрик платежей
- [ ] Полнотекстовый поиск и аналитика по платежам
