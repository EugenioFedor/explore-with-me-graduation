# Explore With Me — microservices

Второй этап проекта разделяет бизнес-функциональность на независимые Spring Boot сервисы. Внешний API остаётся единым: клиент обращается к API Gateway на порту `8080`, а Gateway маршрутизирует запрос в нужный сервис через Eureka.

## Архитектура

- `infra/discovery-server` — Eureka Service Discovery.
- `infra/config-server` — централизованная конфигурация. Конфигурации сервисов находятся в `infra/config-server/src/main/resources/config`.
- `infra/gateway-server` — единая точка входа, порт `8080`.
- `core/user-service` — управление пользователями, отдельная БД `user-db`.
- `core/event-service` — события, категории и подборки, отдельная БД `event-db`.
- `core/request-service` — заявки на участие, отдельная БД `request-db`.
- `core/comment-service` — дополнительная функциональность комментариев, отдельная БД `comment-db`.
- `stats-service` — сервис статистики и его отдельная БД `stats-db`.

Сервисы не используют JPA-связи между разными базами. Вместо объектов других сервисов хранятся идентификаторы (`initiatorId`, `requesterId`, `eventId`, `authorId`). Межсервисные обращения выполняются через OpenFeign, а адреса экземпляров разрешаются через Eureka.

## Внутренний API

Внутренние endpoint'ы не предназначены для внешних клиентов и используются Feign-клиентами:

- `GET /internal/users/{userId}` — получить пользователя для event/request/comment-service.
- `GET /internal/events/{eventId}/request-info` — данные события, необходимые request-service: инициатор, статус, лимит участников и признак модерации.
- `GET /internal/events/{eventId}/comment-info` — проверить событие и его статус для comment-service.
- `GET /internal/requests/events/{eventId}/confirmed-count` — число подтверждённых заявок события.
- `POST /internal/requests/events/confirmed-counts` — числа подтверждённых заявок сразу для набора событий; пакетный метод исключает N+1 сетевых запросов.
- `GET /internal/comments/events/{eventId}/published-count` — число опубликованных комментариев события.
- `POST /internal/comments/events/published-counts` — числа опубликованных комментариев сразу для набора событий.

При недоступности request-service или comment-service event-service возвращает для соответствующих счётчиков значение `0`, поэтому публичное чтение событий не должно падать только из-за недоступности этих сервисов. При недоступности user-service публичное представление события сохраняет id инициатора и возвращает пустое имя.

## Внешний API

Спецификация основного внешнего API находится в корне проекта: `ewm-main-service-spec.json`. Спецификация статистики: `ewm-stats-service-spec.json`. Postman-коллекции находятся в каталоге `postman`.

## Запуск

```bash
docker compose down -v
docker compose up --build
```

После запуска внешний API доступен через Gateway: `http://localhost:8080`.

Перед сдачей рекомендуется выполнить Maven-тесты, затем Postman-коллекции через порт `8080`, после чего проверить деградацию системы при последовательной остановке `comment-service`, `request-service` и `user-service`.
