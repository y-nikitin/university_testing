"use strict";
const { Product, Order, OrderItem, OrderStatus } = require("./models");
function number(value) {
    const result = Number(value);
    if (!Number.isSafeInteger(result)) throw new RangeError("Число БД виходить за безпечні межі");
    return result;
}
function product(row) { return new Product(number(row.id), row.sku, number(row.unit_price), row.stock, row.on_sale); }
function order(row) {
    return new Order(number(row.id), row.reference, row.premium, row.coupon_valid, number(row.total), row.status,
        new OrderItem(number(row.item_id), number(row.product_id), row.quantity, number(row.unit_price), row.on_sale));
}
class ProductRepository {
    constructor(client) { this.client = client; }
    async create(sku, unitPrice, stock, onSale) {
        const r = await this.client.query("INSERT INTO products(sku,unit_price,stock,on_sale) VALUES($1,$2,$3,$4) RETURNING *", [sku,unitPrice,stock,onSale]);
        return product(r.rows[0]);
    }
    async findById(id) {
        const r = await this.client.query("SELECT * FROM products WHERE id=$1", [id]);
        return r.rows.length ? product(r.rows[0]) : null;
    }
    async update(p) {
        const r = await this.client.query("UPDATE products SET sku=$1,unit_price=$2,stock=$3,on_sale=$4 WHERE id=$5", [p.sku,p.unitPrice,p.stock,p.onSale,p.id]);
        if (!r.rowCount) throw new RangeError("Товар не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM products WHERE id=$1", [id]); }
}
class OrderRepository {
    constructor(client) { this.client = client; }
    async create(o) {
        await this.client.query("BEGIN");
        try {
            const r = await this.client.query("INSERT INTO orders(reference,premium,coupon_valid,total,status) VALUES($1,$2,$3,$4,$5) RETURNING id", [o.reference,o.premium,o.couponValid,o.total,o.status]);
            const id = number(r.rows[0].id), i = o.item;
            const r2 = await this.client.query("INSERT INTO order_items(order_id,product_id,quantity,unit_price,on_sale) VALUES($1,$2,$3,$4,$5) RETURNING id", [id,i.productId,i.quantity,i.unitPrice,i.onSale]);
            const saved = new Order(id,o.reference,o.premium,o.couponValid,o.total,o.status,new OrderItem(number(r2.rows[0].id),i.productId,i.quantity,i.unitPrice,i.onSale));
            await this.client.query("COMMIT");
            return saved;
        } catch (error) { await this.client.query("ROLLBACK").catch(() => {}); throw error; }
    }
    async findById(id) {
        const r = await this.client.query(OrderRepository.select + "WHERE o.id=$1", [id]);
        return r.rows.length ? order(r.rows[0]) : null;
    }
    async findAll() {
        const r = await this.client.query(OrderRepository.select + "ORDER BY o.id");
        return r.rows.map(order);
    }
    async findOrders(productId, status) {
        if (!Number.isSafeInteger(productId) || productId <= 0 || !Object.values(OrderStatus).includes(status))
            throw new RangeError("Некоректні аргументи фільтра");
        const r = await this.client.query(OrderRepository.select + "WHERE i.product_id=$1 AND o.status=$2 ORDER BY o.id", [productId,status]);
        return r.rows.map(order);
    }
    async updateStatus(id, status) {
        if (!Object.values(OrderStatus).includes(status)) throw new RangeError("Некоректний статус");
        const r = await this.client.query("UPDATE orders SET status=$1 WHERE id=$2", [status,id]);
        if (!r.rowCount) throw new RangeError("Замовлення не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM orders WHERE id=$1", [id]); }
}
OrderRepository.select = "SELECT o.*,i.id AS item_id,i.product_id,i.quantity,i.unit_price,i.on_sale FROM orders o JOIN order_items i ON i.order_id=o.id ";
module.exports = { ProductRepository, OrderRepository };
