const config = {"python": false, "coupon": true, "returnDetails": false};
const $ = id => document.getElementById(id);
const money = value => new Intl.NumberFormat('uk-UA', {style: 'currency', currency: 'UAH'}).format(value / 100);
const field = name => config.python ? name.replace(/[A-Z]/g, letter => '_' + letter.toLowerCase()) : name;
let order = null;
let product = null;

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
function showOrder(value) {
    order = value;
    $('order-reference').textContent = value.reference;
    $('order-id').textContent = value.id;
    $('order-product').textContent = value.item[field('productId')];
    $('order-quantity').textContent = value.item.quantity;
    $('order-price').textContent = money(value.item[field('unitPrice')]);
    $('order-sale').textContent = value.item[field('onSale')] ? 'Так' : 'Ні';
    $('order-premium').textContent = value.premium ? 'Так' : 'Ні';
    $('order-coupon').textContent = value[field('couponValid')] ? 'Так' : 'Ні';
    $('order-total').textContent = money(value.total);
    $('order-total').dataset.kopecks = value.total;
    $('order-status').textContent = value.status === 'CONFIRMED' ? 'Підтверджено' : value.status === 'RETURNED' ? 'Повернено' : value.status;
    $('order-status').dataset.status = value.status;
    $('lookup-form').elements.id.value = value.id;
    $('order-result').hidden = false;
    history.replaceState(null, '', '?order=' + encodeURIComponent(value.id) + '#order');
}
function showProduct(value) {
    product = value;
    $('product-title').textContent = value.sku;
    $('product-info').textContent = 'ID: ' + value.id + ' · Ціна: ' + money(value[field('unitPrice')]) + ' · Залишок: ' + value.stock + ' · Акційний: ' + (value[field('onSale')] ? 'так' : 'ні');
    $('product-result').hidden = false;
}
bind('product-form', async input => {
    showProduct(await request('/products', {sku: input.sku.value, [field('unitPrice')]: Math.round(Number(input.price.value) * 100), stock: Number(input.stock.value), [field('onSale')]: input.onSale.checked}));
    $('order-form').elements.productId.value = product.id;
    $('notice').textContent = 'Товар створено. Ідентифікатор підставлено у форму замовлення.';
});
bind('product-lookup-form', async input => {
    $('product-result').hidden = true;
    showProduct(await request('/products/' + encodeURIComponent(input.id.value)));
    $('notice').textContent = 'Товар завантажено.';
});
$('use-product').addEventListener('click', () => {
    if (!product) return;
    $('order-form').elements.productId.value = product.id;
    location.hash = 'create';
    $('order-form').elements.reference.focus();
});
bind('order-form', async input => {
    const body = {reference: input.reference.value, [field('productId')]: Number(input.productId.value), quantity: Number(input.quantity.value), premium: input.premium.checked};
    if (config.coupon) body[config.python ? 'coupon_claimed' : 'couponValid'] = input.coupon.checked;
    showOrder(await request('/orders', body));
    $('notice').textContent = 'Замовлення створено.';
    $('order-result').scrollIntoView({block: 'nearest'});
});
async function loadOrder(id) {
    order = null;
    $('order-result').hidden = true;
    showOrder(await request('/orders/' + encodeURIComponent(id)));
    $('notice').textContent = 'Замовлення завантажено.';
}
bind('lookup-form', input => loadOrder(input.id.value));
bind('return-form', async input => {
    if (!order) return;
    const body = config.returnDetails ? {[field('daysSincePurchase')]: Number(input.days.value), [field('packagingOpen')]: input.packaging.checked} : {};
    showOrder(await request('/orders/' + encodeURIComponent(order.id) + '/return', body));
    $('notice').textContent = 'Замовлення повернено.';
});
const initialID = new URLSearchParams(location.search).get('order');
if (initialID) perform($('lookup-form'), () => loadOrder(initialID));
