const config = {"python": false, "coupon": true};
const $ = id => document.getElementById(id);
const key = name => config.python ? name.replace(/[A-Z]/g, c => '_' + c.toLowerCase()) : name;
const money = value => new Intl.NumberFormat('uk-UA', {style:'currency',currency:'UAH'}).format(value / 100);
let enrollment = null;
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

function showEnrollment(value) {
    enrollment = value;
    $('enrollment-result').hidden = false;
    $('enrollment-reference').textContent = value.reference;
    $('enrollment-id').textContent = value.id;
    const statuses = {Enrolled:'Зараховано',Completed:'Завершено',Cancelled:'Скасовано'};
    $('enrollment-status').textContent = statuses[value.status] || value.status;
    $('enrollment-status').dataset.status = value.status;
    for (const name of ['total','prepaid','refund']) {
        $('enrollment-' + name).textContent = money(value[name]);
        $('enrollment-' + name).dataset.kopecks = value[name];
    }
    $('enrollment-details').textContent = 'Слухач ID: ' + value[key('studentId')] + ' · Курс ID: ' + value[key('courseId')];
    $('lookup-form').elements.id.value = value.id;
    history.replaceState(null,'','?enrollment=' + encodeURIComponent(value.id) + '#enrollment');
}
function showResource(type,value) {
    $(type + '-result').hidden = false;
    $(type + '-info').textContent = type === 'student'
        ? value.code + ' · ID: ' + value.id + ' · Вік: ' + value.age + ' · Бал: ' + value[key('entryScore')] + ' · Постійний слухач: ' + (value.regular ? 'так':'ні')
        : value.code + ' · ID: ' + value.id + ' · Ціна: ' + money(value.price) + ' · Місткість: ' + value.capacity + ' · Рівень: ' + value.level;
    $('enrollment-form').elements[type + 'Id'].value = value.id;
}
bind('student-form',async input => {
    showResource('student',await request('/students',{code:input.code.value,age:Number(input.age.value),[key('entryScore')]:Number(input.score.value),regular:input.regular.checked}));
    $('notice').textContent = 'Слухача створено. Ідентифікатор підставлено у заявку.';
});
bind('course-form',async input => {
    showResource('course',await request('/courses',{code:input.code.value,price:Math.round(Number(input.price.value)*100),capacity:Number(input.capacity.value),level:Number(input.level.value)}));
    $('notice').textContent = 'Курс створено. Ідентифікатор підставлено у заявку.';
});
for (const type of ['student','course']) bind(type + '-lookup',async input => {
    $(type + '-result').hidden = true;
    showResource(type,await request('/' + type + 's/' + encodeURIComponent(input.id.value)));
    $('notice').textContent = 'Дані завантажено. Ідентифікатор підставлено у заявку.';
});
bind('enrollment-form',async input => {
    const body = {reference:input.reference.value,[key('studentId')]:Number(input.studentId.value),[key('courseId')]:Number(input.courseId.value)};
    if (config.coupon) body[key('couponValid')] = input.coupon.checked;
    showEnrollment(await request('/enrollments',body));
    $('notice').textContent = 'Слухача зараховано.';
    $('enrollment-result').scrollIntoView({block:'nearest'});
});
async function loadEnrollment(id) {
    enrollment = null;
    $('enrollment-result').hidden = true;
    showEnrollment(await request('/enrollments/' + encodeURIComponent(id)));
    $('notice').textContent = 'Заявку завантажено.';
}
bind('lookup-form',input => loadEnrollment(input.id.value));
$('complete').addEventListener('click',() => {
    if (!enrollment) return;
    const id = enrollment.id;
    perform($('enrollment-actions'),async () => {
        showEnrollment(await request('/enrollments/' + encodeURIComponent(id) + '/complete',{}));
        $('notice').textContent = 'Навчання завершено.';
    });
});
$('cancel-form').addEventListener('submit',event => {
    event.preventDefault();
    if (!enrollment) return;
    const id = enrollment.id;
    const days = Number(event.currentTarget.elements.days.value);
    perform($('enrollment-actions'),async () => {
        showEnrollment(await request('/enrollments/' + encodeURIComponent(id) + '/cancel',{[key('daysBeforeStart')]:days}));
        $('notice').textContent = 'Зарахування скасовано.';
    });
});
const initialID = new URLSearchParams(location.search).get('enrollment');
if (initialID) perform($('lookup-form'),() => loadEnrollment(initialID));

$('welcome').innerHTML = new URLSearchParams(location.search).get('welcome') || 'Вітаємо на навчальних курсах!';
bind('search-form', async input => {
    $('search-result').textContent = JSON.stringify(await request('/enrollments/search?reference=' + encodeURIComponent(input.reference.value)),null,2);
    $('notice').textContent = 'Пошук завершено.';
});
bind('edit-form', async input => {
    if (!enrollment) return;
    const response = await fetch('/enrollments/' + enrollment.id, {method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({reference:input.reference.value})});
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Не вдалося змінити код.');
    showEnrollment(data);
    $('notice').textContent = 'Код змінено.';
});
