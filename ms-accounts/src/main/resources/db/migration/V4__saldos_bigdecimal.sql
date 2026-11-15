-- Los importes y saldos pasan de DOUBLE PRECISION (coma flotante binaria) a NUMERIC(19,2).
-- Con double, 0,10 + 0,20 da 0,30000000000000004. Con NUMERIC la suma es exacta.
-- Los valores existentes se redondean a céntimos; no se modifica ningún movimiento.

ALTER TABLE accounts
    ALTER COLUMN balance TYPE NUMERIC(19,2) USING ROUND(balance::numeric, 2),
    ALTER COLUMN balance SET DEFAULT 0;

ALTER TABLE movements
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric, 2),
    ALTER COLUMN balance_after TYPE NUMERIC(19,2) USING ROUND(balance_after::numeric, 2);
