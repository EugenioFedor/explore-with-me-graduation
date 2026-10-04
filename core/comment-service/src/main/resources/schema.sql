CREATE TABLE IF NOT EXISTS comments
(
    id
    BIGINT
    GENERATED
    BY
    DEFAULT AS
    IDENTITY
    PRIMARY
    KEY,
    text
    VARCHAR
(
    1000
) NOT NULL,
    created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated TIMESTAMP
                      WITHOUT TIME ZONE,
    status VARCHAR
(
    20
) NOT NULL DEFAULT 'PENDING',
    event_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_comments_event_id ON comments(event_id);
CREATE INDEX IF NOT EXISTS idx_comments_status ON comments(status);
CREATE INDEX IF NOT EXISTS idx_comments_author_id ON comments(author_id);
