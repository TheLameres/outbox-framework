CREATE TABLE outbox_events (
    id             UUID         NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(150) NOT NULL,
    payload        TEXT         NOT NULL,
    headers        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt        INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL,
    claimed_at     TIMESTAMPTZ,
    processed_at   TIMESTAMPTZ,
    lock_version   BIGINT       NOT NULL DEFAULT 0
);

-- Частичный индекс: в него попадают только «живые» строки, поэтому он остаётся
-- маленьким даже когда в таблице миллионы PROCESSED-записей.
CREATE INDEX idx_outbox_claimable
    ON outbox_events (created_at)
    WHERE status IN ('PENDING', 'IN_FLIGHT');

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_purge
    ON outbox_events (processed_at)
    WHERE status = 'PROCESSED';
