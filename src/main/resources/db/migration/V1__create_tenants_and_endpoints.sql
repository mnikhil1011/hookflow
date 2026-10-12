-- A tenant is one customer of the platform. It sends events through our API.
CREATE TABLE tenants (
    id           UUID PRIMARY KEY,
    name         TEXT        NOT NULL,
    api_key_hash TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- An endpoint is a URL owned by a tenant that we deliver webhooks to.
CREATE TABLE endpoints (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenants (id),
    url            TEXT        NOT NULL,
    signing_secret TEXT        NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_endpoints_tenant_id ON endpoints (tenant_id);
