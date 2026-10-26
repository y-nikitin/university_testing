const config = {"python": false, "fragile": false, "dimensions": true, "coupon": false};
const $ = id => document.getElementById(id);
const key = name => config.python ? name.replace(/[A-Z]/g, c => '_' + c.toLowerCase()) : name;
const money = value => new Intl.NumberFormat('uk-UA', {style:'currency',currency:'UAH'}).format(value / 100);
let shipment = null;
async function request(path, body) {
    const response = await fetch(path, {method:body === undefined ? 'GET':'POST',headers:{'Content-Type':'application/json'},...(body === undefined ? {}:{body:JSON.stringify(body)})});
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Не вдалося виконати операцію.');
    if (!data) throw new Error('Порожня відповідь сервера.');
    return data;
}
async function perform(form, action) {
    const buttons = [...form.querySelectorAll('button')];
    buttons.forEach(b => b.disabled = true);
    form.setAttribute('aria-busy','true');
    $('error').hidden = true;
    $('notice').textContent = 'Виконується запит…';
    try { await action(); }
    catch (error) {
        $('notice').textContent = '';
        $('error').textContent = error instanceof TypeError ? 'Немає зв’язку із сервером.' : error.message;
        $('error').hidden = false;
        $('error').scrollIntoView({block:'nearest'});
    } finally {
        buttons.forEach(b => b.disabled = false);
        form.removeAttribute('aria-busy');
    }
}
function bind(id, fn) {
    $(id).addEventListener('submit', event => {
        event.preventDefault();
        const form = event.currentTarget;
        perform(form, () => fn(form.elements));
    });
}
function showShipment(value) {
    shipment = value;
    $('shipment-result').hidden = false;
    $('shipment-reference').textContent = value.reference;
    $('shipment-id').textContent = value.id;
    const statuses = {Registered:'Зареєстровано',Dispatched:'Відправлено',Delivered:'Доставлено',Cancelled:'Скасовано'};
    $('shipment-status').textContent = statuses[value.status] || value.status;
    $('shipment-status').dataset.status = value.status;
    for (const name of ['baseFee','adjustment','total','refund']) {
        const element = $('shipment-' + name);
        if (!element) continue;
        element.textContent = money(value[key(name)]);
        element.dataset.kopecks = value[key(name)];
    }
    $('shipment-details').textContent = 'Клієнт ID: ' + value[key('customerId')] + ' · Посилка ID: ' + value[key('parcelId')] + ' · Відстань: ' + value[key('distanceKm')] + ' км · Вага: ' + value[key('weightGrams')] + ' г';
    $('shipment-flags').textContent = 'Постійний клієнт: ' + (value.regular ? 'так':'ні') + ' · Терміново: ' + (value.urgent ? 'так':'ні') + ' · Самовивіз: ' + (value.pickup ? 'так':'ні');
    $('lookup-form').elements.id.value = value.id;
    history.replaceState(null,'','?shipment=' + encodeURIComponent(value.id) + '#shipment');
}
function showResource(type, value) {
    $(type + '-result').hidden = false;
    $(type + '-info').textContent = type === 'customer'
        ? value.code + ' · ID: ' + value.id + ' · Постійний клієнт: ' + (value.regular ? 'так':'ні')
        : value.code + ' · ID: ' + value.id + ' · Вага: ' + value[key('weightGrams')] + ' г · Оголошена вартість: ' + money(value[key('declaredValue')]);
    $('shipment-form').elements[type + 'Id'].value = value.id;
}
bind('customer-form', async input => {
    showResource('customer', await request('/customers',{code:input.code.value,regular:input.regular.checked}));
    $('notice').textContent = 'Клієнта створено. Ідентифікатор підставлено у заявку.';
});
bind('parcel-form', async input => {
    const body = {code:input.code.value,[key('weightGrams')]:Number(input.weight.value),[key('declaredValue')]:Math.round(Number(input.value.value)*100)};
    if (config.fragile) body.fragile = input.fragile.checked;
    if (config.dimensions) for (const name of ['lengthCm','widthCm','heightCm']) body[name] = Number(input[name].value);
    showResource('parcel',await request('/parcels',body));
    $('notice').textContent = 'Посилку створено. Ідентифікатор підставлено у заявку.';
});
for (const type of ['customer','parcel']) bind(type + '-lookup', async input => {
    $(type + '-result').hidden = true;
    showResource(type,await request('/' + (type === 'customer' ? 'customers':'parcels') + '/' + encodeURIComponent(input.id.value)));
    $('notice').textContent = 'Дані завантажено. Ідентифікатор підставлено у заявку.';
});
bind('shipment-form',async input => {
    const body = {reference:input.reference.value,[key('customerId')]:Number(input.customerId.value),[key('parcelId')]:Number(input.parcelId.value),[key('distanceKm')]:Number(input.distance.value),urgent:input.urgent.checked,pickup:input.pickup.checked};
    if (config.coupon) body[key('couponValid')] = input.coupon.checked;
    showShipment(await request('/shipments',body));
    $('notice').textContent = 'Відправлення зареєстровано.';
    $('shipment-result').scrollIntoView({block:'nearest'});
});
async function loadShipment(id) {
    shipment = null;
    $('shipment-result').hidden = true;
    showShipment(await request('/shipments/' + encodeURIComponent(id)));
    $('notice').textContent = 'Відправлення завантажено.';
}
bind('lookup-form',input => loadShipment(input.id.value));
for (const action of ['dispatch','deliver','cancel']) $(action).addEventListener('click',() => {
    if (!shipment) return;
    const id = shipment.id;
    perform($('actions'),async () => {
        showShipment(await request('/shipments/' + encodeURIComponent(id) + '/' + action,{}));
        $('notice').textContent = 'Статус відправлення оновлено.';
    });
});
const initialID = new URLSearchParams(location.search).get('shipment');
if (initialID) perform($('lookup-form'),() => loadShipment(initialID));
