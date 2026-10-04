CREATE TABLE IF NOT EXISTS requests
(
    id
    BIGINT
    GENERATED
    BY
    DEFAULT AS
    IDENTITY
    PRIMARY
    KEY,
    created
    TIMESTAMP
    WITHOUT
    TIME
    ZONE,
    status
    VARCHAR
(
    20
) DEFAULT 'PENDING',
    event_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    CONSTRAINT unique_requester_event UNIQUE
(
    requester_id,
    event_id
)
    );
CREATE INDEX IF NOT EXISTS idx_requests_event_id ON requests(event_id);
CREATE INDEX IF NOT EXISTS idx_requests_requester_id ON requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_requests_status ON requests(status);
