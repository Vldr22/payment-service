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
| POST | `/api/auth/register-client` | Регистрация клиента |
| POST | `/api/auth/sms/send` | Отправка кода подтверждения |
| POST | `/api/auth/sms/verify-client` | Вход клиента по коду |
| POST | `/api/auth/staff/login` | Вход сотрудника |
| PATCH | `/api/auth/staff/set-password` | Установка первичного пароля |
| POST | `/api/auth/logout` | Выход, токен в blacklist |

**Клиент** (`ROLE_USER`)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/payments` | Создание платежа |
| POST | `/api/payments/{id}/confirm` | Подтверждение новой картой |
| POST | `/api/payments/{id}/confirm/saved-card` | Подтверждение сохранённой картой |
| GET | `/api/payments/{id}` | Статус платежа |
| POST | `/api/payments/{id}/refund` | Заявка на возврат |
| POST | `/api/cards` | Привязка карты |
| GET | `/api/cards` | Список карт |
| PATCH | `/api/cards/{id}/default` | Карта по умолчанию |
| DELETE | `/api/cards/{id}` | Удаление карты |
| POST | `/api/subscriptions` | Оформление подписки |
| GET | `/api/subscriptions` | Текущая подписка |
| PATCH | `/api/subscriptions/cancel` | Отмена подписки |
| GET | `/api/subscriptions/billing-history` | История списаний |

**Поддержка и администрирование**

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| GET | `/api/support/refunds/pending` | `ROLE_EMPLOYEE` | Заявки на возврат |
| POST | `/api/support/refunds/{id}/approve` | `ROLE_EMPLOYEE` | Одобрить возврат |
| POST | `/api/support/refunds/{id}/reject` | `ROLE_EMPLOYEE` | Отклонить возврат |
| PATCH | `/api/staff/password` | `ROLE_EMPLOYEE`, `ROLE_ADMIN` | Смена пароля сотрудника |
| POST | `/api/admin/register-employee` | `ROLE_ADMIN` | Создание сотрудника |

**Webhook**

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/webhooks/stripe` | Приём событий Stripe |

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
docker compose up --build
```

Ключи Stripe берутся из тестового режима. Администратор создаётся миграцией из переменных
`ADMIN_*`, пароль остаётся в `.env` и в репозиторий не попадает.

**Swagger UI:** http://localhost:8080/swagger-ui/index.html

Для webhook нужен локальный слушатель, его секрет идёт в `STRIPE_WEBHOOK_SECRET`:

```bash
stripe listen --all-snapshot --forward-to localhost:8080/api/webhooks/stripe
```

## Roadmap

- [x] CI на GitHub Actions, защита main, сканирование секретов
- [x] Docker Compose
- [ ] Расширенное управление для администратора: фильтрация платежей, подписок, пользователей
- [ ] Email уведомления о статусе платежей и подписок
- [ ] Prometheus + Grafana мониторинг метрик платежей
- [ ] Полнотекстовый поиск и аналитика по платежам
