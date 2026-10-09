-- Таблица категорий
CREATE TABLE IF NOT EXISTS categories
(
    id
    BIGINT
    GENERATED
    BY
    DEFAULT AS
    IDENTITY
    PRIMARY
    KEY,
    name
    VARCHAR
(
    50
) NOT NULL UNIQUE
    );

-- Таблица событий
CREATE TABLE IF NOT EXISTS events
(
    id
    BIGINT
    GENERATED
    BY
    DEFAULT AS
    IDENTITY
    PRIMARY
    KEY,
    annotation
    VARCHAR
(
    2000
) NOT NULL,
    description VARCHAR
(
    7000
) NOT NULL,
    title VARCHAR
(
    120
) NOT NULL,
    event_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_on TIMESTAMP
                         WITHOUT TIME ZONE,
    published_on TIMESTAMP
                         WITHOUT TIME ZONE,
    paid BOOLEAN DEFAULT FALSE,
    participant_limit INTEGER DEFAULT 0,
    request_moderation BOOLEAN DEFAULT TRUE,
    state VARCHAR
(
    20
) DEFAULT 'PENDING',
    lat FLOAT,
    lon FLOAT,
    initiator_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT fk_event_category FOREIGN KEY
(
    category_id
) REFERENCES categories
(
    id
)
    );

-- Таблица подборок событий
CREATE TABLE IF NOT EXISTS compilations
(
    id
    BIGINT
    GENERATED
    BY
    DEFAULT AS
    IDENTITY
    PRIMARY
    KEY,
    title
    VARCHAR
(
    50
) NOT NULL UNIQUE,
    pinned BOOLEAN DEFAULT FALSE
    );

-- Связующая таблица для подборок и событий (ManyToMany)
CREATE TABLE IF NOT EXISTS compilation_events
(
    compilation_id
    BIGINT
    NOT
    NULL,
    event_id
    BIGINT
    NOT
    NULL,
    PRIMARY
    KEY
(
    compilation_id,
    event_id
),
    CONSTRAINT fk_compilation FOREIGN KEY
(
    compilation_id
) REFERENCES compilations
(
    id
) ON DELETE CASCADE,
    CONSTRAINT fk_event FOREIGN KEY
(
    event_id
) REFERENCES events
(
    id
)
  ON DELETE CASCADE
    );

