CREATE TABLE account_holders (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_account_holders UNIQUE (account_id, user_id)
);

CREATE INDEX idx_account_holders_user ON account_holders (user_id);

INSERT INTO account_holders (account_id, user_id)
SELECT id, owner_id FROM accounts;

DROP INDEX IF EXISTS idx_accounts_owner;
ALTER TABLE accounts DROP COLUMN owner_id;
