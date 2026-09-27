BEGIN;
CREATE TABLE equipment (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
 daily_rate BIGINT NOT NULL CHECK(daily_rate BETWEEN 100 AND 1000000),
 stock INTEGER NOT NULL CHECK(stock >= 0)
);
CREATE TABLE rentals (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reference VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(reference)) > 0),
 days INTEGER NOT NULL CHECK(days BETWEEN 1 AND 30),
 total BIGINT NOT NULL CHECK(total >= 0),
 late_fee BIGINT NOT NULL CHECK(late_fee BETWEEN 0 AND total),
 status VARCHAR(16) NOT NULL CHECK(status IN ('Active','Returned'))
);
CREATE TABLE rental_items (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 rental_id BIGINT NOT NULL UNIQUE REFERENCES rentals(id) ON DELETE CASCADE,
 equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE RESTRICT,
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 20),
 daily_rate BIGINT NOT NULL CHECK(daily_rate BETWEEN 100 AND 1000000)
);
CREATE INDEX rental_items_equipment ON rental_items(equipment_id);
CREATE INDEX rentals_status ON rentals(status,id);
COMMIT;
