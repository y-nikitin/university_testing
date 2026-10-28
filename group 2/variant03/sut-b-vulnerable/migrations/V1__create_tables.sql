BEGIN;
CREATE TABLE customers (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
 regular BOOLEAN NOT NULL
);
CREATE TABLE parcels (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
 weight_grams INTEGER NOT NULL CHECK(weight_grams BETWEEN 1 AND 30000),
 declared_value BIGINT NOT NULL CHECK(declared_value BETWEEN 0 AND 100000000)
);
CREATE TABLE shipments (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reference VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(reference)) > 0),
 customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
 parcel_id BIGINT NOT NULL REFERENCES parcels(id) ON DELETE RESTRICT,
 distance_km INTEGER NOT NULL CHECK(distance_km BETWEEN 1 AND 5000),
 urgent BOOLEAN NOT NULL,
 pickup BOOLEAN NOT NULL,
 regular BOOLEAN NOT NULL,
 weight_grams INTEGER NOT NULL CHECK(weight_grams BETWEEN 1 AND 30000),
 declared_value BIGINT NOT NULL CHECK(declared_value BETWEEN 0 AND 100000000),
 base_fee BIGINT NOT NULL CHECK(base_fee BETWEEN 6010 AND 85000),
 adjustment BIGINT NOT NULL CHECK(adjustment BETWEEN -10000 AND 1000000),
 total BIGINT NOT NULL CHECK(total >= 0 AND total = base_fee + adjustment),
 status VARCHAR(16) NOT NULL CHECK(status IN ('Registered','Dispatched','Delivered','Cancelled'))
);
CREATE INDEX shipments_customer_status ON shipments(customer_id,status,id);
CREATE INDEX shipments_parcel ON shipments(parcel_id);
COMMIT;
