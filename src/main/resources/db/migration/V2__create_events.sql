-- An event is one thing that happened, sent in by a tenant.
CREATE TABLE events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    event_type      TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    idempotency_key TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Empty until the event's ID has been published to Kafka (the outbox pattern).
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_events_tenant_idempotency_key UNIQUE (tenant_id, idempotency_key)
);

-- Lets the outbox relay find unpublished events without scanning the whole table.
CREATE INDEX idx_events_unpublished ON events (created_at) WHERE published_at IS NULL;
