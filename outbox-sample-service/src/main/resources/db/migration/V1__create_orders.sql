CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id UUID           NOT NULL,
    total       NUMERIC(19, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);
