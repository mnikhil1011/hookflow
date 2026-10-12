-- A delivery is the job of getting one event to one endpoint.
CREATE TABLE deliveries (
    id              UUID PRIMARY KEY,
    event_id        UUID        NOT NULL REFERENCES events (id),
    endpoint_id     UUID        NOT NULL REFERENCES endpoints (id),
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    status          TEXT        NOT NULL,
    attempt_count   INT         NOT NULL DEFAULT 0,
    -- The time by which a worker should next act on this row. NULL once the delivery is finished.
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Makes fan-out safe to run twice for the same event.
    CONSTRAINT uq_deliveries_event_endpoint UNIQUE (event_id, endpoint_id)
);

-- Lets the delivery scheduler find due work quickly; finished deliveries are not in the index.
CREATE INDEX idx_deliveries_due ON deliveries (next_attempt_at) WHERE next_attempt_at IS NOT NULL;
CREATE INDEX idx_deliveries_tenant_created ON deliveries (tenant_id, created_at DESC);

-- An attempt is a single HTTP request made for a delivery.
CREATE TABLE delivery_attempts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id      UUID        NOT NULL REFERENCES deliveries (id),
    attempt_number   INT         NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    duration_ms      INT         NOT NULL,
    -- NULL when no response arrived (timeout or connection error).
    response_status  INT,
    error            TEXT,
    response_snippet TEXT
);

CREATE INDEX idx_delivery_attempts_delivery ON delivery_attempts (delivery_id);
