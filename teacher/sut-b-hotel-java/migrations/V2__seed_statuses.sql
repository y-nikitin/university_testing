BEGIN;
INSERT INTO booking_status(code, title) VALUES
    ('CONFIRMED', 'Підтверджено'),
    ('CANCELLED', 'Скасовано');
COMMIT;
