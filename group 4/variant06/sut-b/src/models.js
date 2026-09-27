"use strict";
const OrderStatus = Object.freeze({ CONFIRMED: "CONFIRMED", RETURNED: "RETURNED" });
class Product {
    constructor(id, sku, unitPrice, stock, onSale) {
        Object.assign(this, { id, sku, unitPrice, stock, onSale }); Object.freeze(this);
    }
}
class OrderItem {
    constructor(id, productId, quantity, unitPrice, onSale) {
        Object.assign(this, { id, productId, quantity, unitPrice, onSale }); Object.freeze(this);
    }
}
class Order {
    constructor(id, reference, premium, couponValid, total, status, item) {
        Object.assign(this, { id, reference, premium, couponValid, total, status, item }); Object.freeze(this);
    }
}
class OrderRequest {
    constructor(reference, productId, quantity, premium) {
        Object.assign(this, { reference, productId, quantity, premium }); Object.freeze(this);
    }
}
module.exports = { Product, OrderItem, Order, OrderRequest, OrderStatus };
