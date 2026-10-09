# gRPC и Kafka API

Схемы: `stats-proto/src/main/proto` и `stats-avro/src/main/avro`.

## Collector

`stats.service.collector.UserActionController/CollectUserAction` — unary RPC. Принимает `UserActionProto` (int64 user_id/event_id, ActionTypeProto, google.protobuf.Timestamp), возвращает `google.protobuf.Empty` после записи в Kafka. Некорректные идентификаторы, timestamp или enum: `INVALID_ARGUMENT`; ошибка брокера: `UNAVAILABLE`.

Топик `stats.user-actions.v1`: ключ `Long` (userId), значение — binary Avro `ru.practicum.ewm.stats.avro.UserActionAvro`, без Schema Registry и без Confluent framing. Timestamp имеет точность миллисекунд.

## Aggregator

Топик `stats.events-similarity.v1`: ключ `String` (`eventA:eventB`), значение binary Avro `EventSimilarityAvro`; `eventA < eventB`, score — double. Timestamp взят из действия, инициировавшего перерасчёт.

## Analyzer

Все методы `stats.service.dashboard.RecommendationsController` возвращают server-streaming `RecommendedEventProto` с int64 event_id и double score:

- `GetRecommendationsForUser(UserPredictionsRequestProto)` — user_id, max_results; score — персональный прогноз.
- `GetSimilarEvents(SimilarEventsRequestProto)` — event_id, user_id, max_results; score — сходство. Исключены события, с которыми пользователь уже взаимодействовал.
- `GetInteractionsCount(InteractionsCountRequestProto)` — repeated event_id; score — сумма максимальных весов пользователей. Для неизвестного мероприятия возвращает 0; пустой запрос даёт пустой поток. Повторяющиеся id не дублируются.

Для запросов рекомендаций max_results=0 даёт пустой поток. Отрицательные лимиты и неположительные id: INVALID_ARGUMENT. Отсутствие истории — нормальный пустой результат.

Порт gRPC выбирается случайно и публикуется в Eureka; при локальной диагностике его можно получить из метаданных `gRPC_port` приложений COLLECTOR/ANALYZER. Рекомендуемый способ вызова — клиенты из stats-client через discovery.
