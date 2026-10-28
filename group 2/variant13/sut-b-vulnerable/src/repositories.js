"use strict";
const { Customer, Parcel, Shipment, ShipmentStatus } = require("./models");
const { integer, ShipmentRejectedError } = require("./validation");
function number(value) {
    const result = Number(value);
    if (!Number.isSafeInteger(result)) throw new RangeError("Число БД не є безпечним цілим");
    return result;
}
function mapCustomer(r) { return new Customer(number(r.id), r.code, r.regular); }
function mapParcel(r) { return new Parcel(number(r.id), r.code, r.weight_grams, number(r.declared_value), r.length_cm, r.width_cm, r.height_cm); }
function mapShipment(r) {
    return new Shipment({ id: number(r.id), reference: r.reference, customerId: number(r.customer_id), parcelId: number(r.parcel_id),
        distanceKm: r.distance_km, urgent: r.urgent, pickup: r.pickup, regular: r.regular, weightGrams: r.weight_grams,
        declaredValue: number(r.declared_value), lengthCm: r.length_cm, widthCm: r.width_cm, heightCm: r.height_cm,
        baseFee: number(r.base_fee), adjustment: number(r.adjustment), total: number(r.total), refund: number(r.refund), status: r.status });
}
class CustomerRepository {
    constructor(client) { this.client = client; }
    async create(customer) {
        const result = await this.client.query("INSERT INTO customers(code,regular) VALUES ($1,$2) RETURNING *", [customer.code, customer.regular]);
        return mapCustomer(result.rows[0]);
    }
    async findById(id) {
        const result = await this.client.query("SELECT * FROM customers WHERE id=$1", [id]);
        return result.rows.length ? mapCustomer(result.rows[0]) : null;
    }
    async update(customer) {
        const result = await this.client.query("UPDATE customers SET code=$1,regular=$2 WHERE id=$3", [customer.code, customer.regular, customer.id]);
        if (!result.rowCount) throw new RangeError("Клієнта не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM customers WHERE id=$1", [id]); }
}
class ParcelRepository {
    constructor(client) { this.client = client; }
    async create(parcel) {
        const result = await this.client.query("INSERT INTO parcels(code,weight_grams,declared_value,length_cm,width_cm,height_cm) VALUES ($1,$2,$3,$4,$5,$6) RETURNING *",
            [parcel.code, parcel.weightGrams, parcel.declaredValue, parcel.lengthCm, parcel.widthCm, parcel.heightCm]);
        return mapParcel(result.rows[0]);
    }
    async findById(id) {
        const result = await this.client.query("SELECT * FROM parcels WHERE id=$1", [id]);
        return result.rows.length ? mapParcel(result.rows[0]) : null;
    }
    async update(parcel) {
        const result = await this.client.query("UPDATE parcels SET code=$1,weight_grams=$2,declared_value=$3,length_cm=$4,width_cm=$5,height_cm=$6 WHERE id=$7",
            [parcel.code, parcel.weightGrams, parcel.declaredValue, parcel.lengthCm, parcel.widthCm, parcel.heightCm, parcel.id]);
        if (!result.rowCount) throw new RangeError("Посилку не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM parcels WHERE id=$1", [id]); }
}
class ShipmentRepository {
    constructor(client) { this.client = client; }
    async create(s) {
        const result = await this.client.query("INSERT INTO shipments(reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,weight_grams,declared_value,length_cm,width_cm,height_cm,base_fee,adjustment,total,refund,status) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17) RETURNING *",
            [s.reference, s.customerId, s.parcelId, s.distanceKm, s.urgent, s.pickup, s.regular, s.weightGrams, s.declaredValue,
             s.lengthCm, s.widthCm, s.heightCm, s.baseFee, s.adjustment, s.total, s.refund, s.status]);
        return mapShipment(result.rows[0]);
    }
    async findById(id) {
        const result = await this.client.query("SELECT * FROM shipments WHERE id=$1", [id]);
        return result.rows.length ? mapShipment(result.rows[0]) : null;
    }
    async findShipments(customerId, status) {
        integer(customerId, 1, Number.MAX_SAFE_INTEGER);
        if (!Object.values(ShipmentStatus).includes(status)) throw new RangeError("Некоректний статус");
        const result = await this.client.query("SELECT * FROM shipments WHERE customer_id=$1 AND status=$2 ORDER BY id", [customerId, status]);
        return result.rows.map(mapShipment);
    }
    async updateStatus(id, expected, next, refund) {
        integer(refund, 0, 135000);
        const allowed = (expected === ShipmentStatus.REGISTERED && [ShipmentStatus.DISPATCHED, ShipmentStatus.CANCELLED].includes(next))
            || (expected === ShipmentStatus.DISPATCHED && next === ShipmentStatus.DELIVERED);
        if (!allowed) throw new RangeError("Перехід статусу заборонений");
        const result = await this.client.query("UPDATE shipments SET status=$1,refund=$2 WHERE id=$3 AND status=$4", [next, refund, id, expected]);
        if (!result.rowCount) throw new ShipmentRejectedError("Відправлення відсутнє або його статус уже змінено");
    }
    async delete(id) { await this.client.query("DELETE FROM shipments WHERE id=$1", [id]); }
}
module.exports = { CustomerRepository, ParcelRepository, ShipmentRepository };
