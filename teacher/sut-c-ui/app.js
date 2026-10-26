const $ = (id) => document.getElementById(id);
const money = (kopecks) => new Intl.NumberFormat('uk-UA', {style: 'currency', currency: 'UAH'}).format(kopecks / 100);
let booking = null;
let room = null;

async function request(path, body) {
    const response = await fetch(path, {
        method: body === undefined ? 'GET' : 'POST',
        headers: {'Content-Type': 'application/json'},
        ...(body === undefined ? {} : {body: JSON.stringify(body)})
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Не вдалося виконати операцію.');
    if (!data) throw new Error('Сервер повернув порожню відповідь.');
    return data;
}

async function perform(element, action) {
    const buttons = element.matches('button') ? [element] : [...element.querySelectorAll('button')];
    buttons.forEach(button => button.disabled = true);
    $('error').hidden = true;
    $('notice').textContent = 'Виконується запит…';
    element.setAttribute('aria-busy', 'true');
    try {
        await action();
    } catch (error) {
        $('notice').textContent = '';
        $('error').textContent = error instanceof TypeError ? 'Немає зв’язку із сервером. Перевірте підключення та повторіть спробу.' : error.message;
        $('error').hidden = false;
        $('error').scrollIntoView({block: 'nearest'});
    } finally {
        buttons.forEach(button => button.disabled = false);
        element.removeAttribute('aria-busy');
    }
}

function showBooking(value) {
    booking = value;
    $('booking-reference').textContent = value.reference;
    $('booking-id').textContent = value.id;
    $('booking-room').textContent = value.roomId;
    $('booking-nights').textContent = value.nights;
    $('booking-guests').textContent = value.guests;
    $('booking-total').textContent = money(value.total);
    $('booking-total').dataset.kopecks = value.total;
    $('booking-status').textContent = value.status === 'CONFIRMED' ? 'Підтверджено' : value.status === 'CANCELLED' ? 'Скасовано' : value.status;
    $('booking-status').dataset.status = value.status;
    $('lookup-form').elements.id.value = value.id;
    $('booking-result').hidden = false;
    history.replaceState(null, '', '?booking=' + encodeURIComponent(value.id) + '#booking');
}

function showRoom(value) {
    room = value;
    $('room-title').textContent = 'Кімната ' + value.number;
    $('room-info').textContent = 'ID: ' + value.id + ' · Місткість: ' + value.capacity + ' · За ніч: ' + money(value.nightlyPrice);
    $('room-result').hidden = false;
}

$('room-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget;
    perform(form, async () => {
        showRoom(await request('/rooms', {number: form.elements.number.value, capacity: Number(form.elements.capacity.value), nightlyPrice: Math.round(Number(form.elements.price.value) * 100)}));
        $('booking-form').elements.roomId.value = room.id;
        $('notice').textContent = 'Номер створено. Його ідентифікатор підставлено у форму бронювання.';
    });
});
$('room-lookup-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget;
    $('room-result').hidden = true;
    perform(form, async () => {
        showRoom(await request('/rooms/' + encodeURIComponent(form.elements.id.value)));
        $('notice').textContent = 'Номер знайдено.';
    });
});
$('use-room').addEventListener('click', () => {
    if (!room) return;
    $('booking-form').elements.roomId.value = room.id;
    location.hash = 'create';
    $('booking-form').elements.reference.focus();
});
$('booking-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget;
    perform(form, async () => {
        showBooking(await request('/bookings', {reference: form.elements.reference.value, roomId: Number(form.elements.roomId.value), nights: Number(form.elements.nights.value), guests: Number(form.elements.guests.value)}));
        $('notice').textContent = 'Бронювання створено.';
        $('booking-result').scrollIntoView({block: 'nearest'});
    });
});
async function loadBooking(id) {
    booking = null;
    $('booking-result').hidden = true;
    showBooking(await request('/bookings/' + encodeURIComponent(id)));
    $('notice').textContent = 'Бронювання завантажено.';
}
$('lookup-form').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.currentTarget;
    perform(form, () => loadBooking(form.elements.id.value));
});
$('cancel-booking').addEventListener('click', event => {
    if (!booking) return;
    const id = booking.id;
    perform(event.currentTarget, async () => {
        showBooking(await request('/bookings/' + encodeURIComponent(id) + '/cancel', {}));
        $('notice').textContent = 'Бронювання скасовано.';
    });
});
const initialID = new URLSearchParams(location.search).get('booking');
if (initialID) perform($('lookup-form'), () => loadBooking(initialID));
