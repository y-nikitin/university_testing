BEGIN;
CREATE TABLE booking_status (
    code VARCHAR(16) PRIMARY KEY,
    title TEXT NOT NULL UNIQUE
);
CREATE TABLE rooms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number VARCHAR(10) NOT NULL UNIQUE CHECK (length(trim(number)) > 0),
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 4),
    nightly_price BIGINT NOT NULL CHECK (nightly_price BETWEEN 100 AND 10000000)
);
CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference VARCHAR(40) NOT NULL UNIQUE CHECK (length(trim(reference)) > 0),
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    nights INTEGER NOT NULL CHECK (nights BETWEEN 1 AND 30),
    guests INTEGER NOT NULL CHECK (guests BETWEEN 1 AND 4),
    total BIGINT NOT NULL CHECK (total >= 0),
    status VARCHAR(16) NOT NULL REFERENCES booking_status(code) ON DELETE RESTRICT
);
CREATE INDEX bookings_room_status_idx ON bookings(room_id, status);
COMMIT;
