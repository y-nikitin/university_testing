"use strict";
const { Order, OrderItem, OrderStatus, OrderRequest } = require("./models");
class OrderRejectedError extends Error {}
function integer(value, min, max, field) {
    if (!Number.isSafeInteger(value) || value < min || value > max) throw new RangeError("Некоректне значення: " + field);
}
class OrderService {
    constructor(products, orders, policy) {
        if (!products || !orders || !policy) throw new TypeError("Усі залежності мають бути задані");
        Object.assign(this, { products, orders, policy });
    }
    calculateTotal(unitPrice, quantity, premium) {
        integer(unitPrice,100,1000000,"Ціна"); integer(quantity,1,20,"Кількість");
        if (typeof premium !== "boolean") throw new TypeError("Некоректна ознака преміальності");
        const subtotal = unitPrice * quantity;
        const discount = subtotal >= 90000 && premium ? 10 : 0;
        return subtotal - Math.floor(subtotal * discount / 100);
    }
    async placeOrder(request) {
        if (!(request instanceof OrderRequest)) throw new TypeError("Некоректна заявка");
        if (typeof request.reference !== "string" || !request.reference.trim() || request.reference.length > 40)
            throw new RangeError("Код замовлення має містити 1–40 символів");
        integer(request.productId,1,Number.MAX_SAFE_INTEGER,"Ідентифікатор товару");
        integer(request.quantity,1,20,"Кількість");
        if (typeof request.premium !== "boolean") throw new TypeError("Некоректна ознака преміальності");
        const product = await this.products.findById(request.productId);
        if (!product) throw new RangeError("Товар не знайдено");
        const limit = request.premium ? 10 : 6;
        if (request.quantity > limit) throw new OrderRejectedError("Перевищено ліміт кількості");
        if (request.quantity > product.stock) throw new OrderRejectedError("Недостатньо товару");
        const allowed = await this.policy.isAllowed(product, request.quantity);
        if (typeof allowed !== "boolean") throw new TypeError("Некоректна відповідь зовнішньої перевірки");
        if (!allowed) throw new OrderRejectedError("Оформлення не дозволено");
        const total = this.calculateTotal(product.unitPrice, request.quantity, request.premium);
        const item = new OrderItem(0,product.id,request.quantity,product.unitPrice,product.onSale);
        return await this.orders.create(new Order(0,request.reference,request.premium,false,total,OrderStatus.CONFIRMED,item));
    }
    async returnOrder(id, daysSincePurchase, packagingOpen = false) {
        integer(id,1,Number.MAX_SAFE_INTEGER,"Ідентифікатор замовлення");
        integer(daysSincePurchase,0,36500,"Дні після покупки");
        if (typeof packagingOpen !== "boolean") throw new TypeError("Некоректна ознака пакування");
        const order = await this.orders.findById(id);
        if (!order) throw new RangeError("Замовлення не знайдено");
        if (order.status === OrderStatus.RETURNED) throw new OrderRejectedError("Замовлення вже повернено");
        if (daysSincePurchase > 5 || order.item.onSale) throw new OrderRejectedError("Повернення не дозволене");
        await this.orders.updateStatus(id,OrderStatus.RETURNED);
        return new Order(order.id,order.reference,order.premium,order.couponValid,order.total,OrderStatus.RETURNED,order.item);
    }
}
module.exports = { OrderService, OrderRejectedError };
