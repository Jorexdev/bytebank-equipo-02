ALTER TABLE card_payments ADD COLUMN reference VARCHAR(64);

UPDATE card_payments
SET reference = 'CARD-' || id
WHERE reference IS NULL;

ALTER TABLE card_payments ALTER COLUMN reference SET NOT NULL;

CREATE UNIQUE INDEX uq_card_payments_reference ON card_payments (reference);
