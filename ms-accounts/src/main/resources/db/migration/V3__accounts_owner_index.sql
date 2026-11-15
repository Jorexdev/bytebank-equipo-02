-- La lista de cuentas de un cliente filtra por titular
CREATE INDEX idx_accounts_owner ON accounts (owner_id);
