CREATE TABLE IF NOT EXISTS user_interactions (
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    weight DOUBLE PRECISION NOT NULL CHECK (weight >= 0 AND weight <= 1),
    interacted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, event_id)
);
CREATE INDEX IF NOT EXISTS interactions_event_idx ON user_interactions(event_id);
CREATE INDEX IF NOT EXISTS interactions_recent_idx ON user_interactions(user_id, interacted_at DESC);
CREATE TABLE IF NOT EXISTS event_similarities (
    event_a BIGINT NOT NULL,
    event_b BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL CHECK (score >= 0 AND score <= 1.000000001),
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_a, event_b),
    CHECK (event_a < event_b)
);
CREATE INDEX IF NOT EXISTS similarities_second_idx ON event_similarities(event_b);
