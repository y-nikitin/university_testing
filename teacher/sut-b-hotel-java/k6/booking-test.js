import http from 'k6/http';
import { check, fail } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    stages: [
        { duration: '5s', target: 5 },
        { duration: '10s', target: 5 },
        { duration: '2s', target: 100 },
        { duration: '15s', target: 100 },
        { duration: '2s', target: 5 },
        { duration: '10s', target: 5 },
        { duration: '5s', target: 0 },
    ],

    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

const BASE_URL = 'http://127.0.0.1:8080';

export default function () {
    // 1. Create booking
    const reference = uuidv4();

    const payload = JSON.stringify({
        reference,
        roomId: 1,
        nights: 3,
        guests: 2,
    });

    const createResponse = http.post(
        `${BASE_URL}/bookings`,
        payload,
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'POST /bookings' },
        }
    );
    check(createResponse, {
        'POST returns 201': (r) => r.status === 201,
    });

    if (createResponse.status !== 201) {
        fail(`Booking creation failed: ${createResponse.body}`);
    }

    const bookingId = createResponse.json('id');

    // 2. Get booking
    const getResponse = http.get(
        `${BASE_URL}/bookings/${bookingId}`,
        {
            tags: { name: 'GET /bookings/{id}' },
        }
    );

    check(getResponse, {
        'GET returns 200': (r) => r.status === 200,
        'Booking is confirmed': (r) =>
            r.json('status') === 'CONFIRMED',
    });

    // 3. Cancel booking
    const cancelResponse = http.post(
        `${BASE_URL}/bookings/${bookingId}/cancel`,
        null,
        {
            tags: { name: 'POST /bookings/{id}/cancel' },
        }
    );

    check(cancelResponse, {
        'CANCEL returns 200': (r) => r.status === 200,
        'Booking is cancelled': (r) =>
            r.json('status') === 'CANCELLED',
    });
}
