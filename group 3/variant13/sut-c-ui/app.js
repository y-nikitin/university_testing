const config = {"python": false, "returnDetails": true, "deposit": false};
const $ = id => document.getElementById(id);
const money = value => new Intl.NumberFormat('uk-UA', {style: 'currency', currency: 'UAH'}).format(value / 100);
const field = name => config.python ? name.replace(/[A-Z]/g, letter => '_' + letter.toLowerCase()) : name;
let rental = null;
let equipment = null;

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
async function perform(form, action) {
    const buttons = [...form.querySelectorAll('button')];
    buttons.forEach(button => button.disabled = true);
    form.setAttribute('aria-busy', 'true');
    $('error').hidden = true;
    $('notice').textContent = 'Виконується запит…';
    try { await action(); }
    catch (error) {
        $('notice').textContent = '';
        $('error').textContent = error instanceof TypeError ? 'Немає зв’язку із сервером. Перевірте підключення.' : error.message;
        $('error').hidden = false;
        $('error').scrollIntoView({block: 'nearest'});
    } finally {
        buttons.forEach(button => button.disabled = false);
        form.removeAttribute('aria-busy');
    }
}
function bind(id, action) {
    $(id).addEventListener('submit', event => {
        event.preventDefault();
        perform(event.currentTarget, () => action(event.currentTarget.elements));
    });
}
function showRental(value) {
    rental = value;
    $('rental-reference').textContent = value.reference;
    $('rental-id').textContent = value.id;
    $('rental-equipment').textContent = value.item[field('equipmentId')];
    $('rental-quantity').textContent = value.item.quantity;
    $('rental-days').textContent = value.days;
    $('rental-late-fee').textContent = money(value[field('lateFee')]);
    $('rental-late-fee').dataset.kopecks = value[field('lateFee')];
    if (config.deposit) {
        $('rental-deposit').textContent = money(value.deposit);
        $('rental-deposit').dataset.kopecks = value.deposit;
    }
    $('rental-price').textContent = money(value.item[field('dailyRate')]);
    $('rental-premium').textContent = value.premium ? 'Так' : 'Ні';
    $('rental-total').textContent = money(value.total);
    $('rental-total').dataset.kopecks = value.total;
    $('rental-status').textContent = value.status === 'Active' ? 'Активна' : value.status === 'Returned' ? 'Повернено' : value.status;
    $('rental-status').dataset.status = value.status;
    $('lookup-form').elements.id.value = value.id;
    $('rental-result').hidden = false;
    history.replaceState(null, '', '?rental=' + encodeURIComponent(value.id) + '#rental');
}
function showEquipment(value) {
    equipment = value;
    $('equipment-title').textContent = value.code;
    $('equipment-info').textContent = 'ID: ' + value.id + ' · Ціна: ' + money(value[field('dailyRate')]) + ' · Залишок: ' + value.stock;
    $('equipment-result').hidden = false;
}
bind('equipment-form', async input => {
    showEquipment(await request('/equipment', {code: input.code.value, [field('dailyRate')]: Math.round(Number(input.price.value) * 100), stock: Number(input.stock.value)}));
    $('rental-form').elements.equipmentId.value = equipment.id;
    $('notice').textContent = 'Обладнання створено. Ідентифікатор підставлено у форму оренди.';
});
bind('equipment-lookup-form', async input => {
    $('equipment-result').hidden = true;
    showEquipment(await request('/equipment/' + encodeURIComponent(input.id.value)));
    $('notice').textContent = 'Обладнання завантажено.';
});
$('use-equipment').addEventListener('click', () => {
    if (!equipment) return;
    $('rental-form').elements.equipmentId.value = equipment.id;
    location.hash = 'create';
    $('rental-form').elements.reference.focus();
});
bind('rental-form', async input => {
    const body = {reference: input.reference.value, [field('equipmentId')]: Number(input.equipmentId.value), quantity: Number(input.quantity.value), days: Number(input.days.value), premium: input.premium.checked};
    showRental(await request('/rentals', body));
    $('notice').textContent = 'Оренду оформлено.';
    $('rental-result').scrollIntoView({block: 'nearest'});
});
async function loadRental(id) {
    rental = null;
    $('rental-result').hidden = true;
    showRental(await request('/rentals/' + encodeURIComponent(id)));
    $('notice').textContent = 'Оренду завантажено.';
}
bind('lookup-form', input => loadRental(input.id.value));
bind('return-form', async input => {
    if (!rental) return;
    const body = config.returnDetails ? {[field('overdueDays')]: Number(input.overdue.value)} : {};
    showRental(await request('/rentals/' + encodeURIComponent(rental.id) + '/return', body));
    $('notice').textContent = 'Обладнання повернено.';
});
const initialID = new URLSearchParams(location.search).get('rental');
if (initialID) perform($('lookup-form'), () => loadRental(initialID));
