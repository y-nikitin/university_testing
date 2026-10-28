"use strict";
const { Shipment, ShipmentRequest, ShipmentStatus } = require("./models");
const { integer, boolean, dimensions, ShipmentRejectedError } = require("./validation");
class ResourceNotFoundError extends RangeError {}
class ShippingService {
    constructor(customers, parcels, shipments, policy) {
        if (!customers || !parcels || !shipments || !policy) throw new TypeError("Усі залежності обов'язкові");
        Object.assign(this, { customers, parcels, shipments, policy });
    }
    calculateBaseFee(weightGrams, distanceKm) {
        integer(weightGrams, 1, 30000); integer(distanceKm, 1, 5000);
        return 5000 + Math.ceil(weightGrams / 1000) * 1000 + distanceKm * 10;
    }
    isParcelAllowed(weightGrams, lengthCm, widthCm, heightCm) {
        integer(weightGrams, 1, 30000); dimensions(lengthCm, widthCm, heightCm);
        return weightGrams <= 20000 && Math.max(lengthCm, widthCm, heightCm) <= 100;
    }
    calculateRefund(total) {
        integer(total, 0, 135000);
        return total;
    }
    async createShipment(request) {
        if (!(request instanceof ShipmentRequest)) throw new TypeError("Некоректна заявка");
        if (typeof request.reference !== "string" || !request.reference.trim() || request.reference.length > 40)
            throw new RangeError("Код відправлення має містити 1–40 символів");
        integer(request.customerId, 1, Number.MAX_SAFE_INTEGER); integer(request.parcelId, 1, Number.MAX_SAFE_INTEGER);
        integer(request.distanceKm, 1, 5000); boolean(request.urgent); boolean(request.pickup);
        const customer = await this.customers.findById(request.customerId);
        if (!customer) throw new ResourceNotFoundError("Клієнта не знайдено");
        const parcel = await this.parcels.findById(request.parcelId);
        if (!parcel) throw new ResourceNotFoundError("Посилку не знайдено");
        boolean(customer.regular); integer(parcel.declaredValue, 0, 100000000);
        dimensions(parcel.lengthCm, parcel.widthCm, parcel.heightCm);
        const baseFee = this.calculateBaseFee(parcel.weightGrams, request.distanceKm);
        if (!this.isParcelAllowed(parcel.weightGrams, parcel.lengthCm, parcel.widthCm, parcel.heightCm))
            throw new ShipmentRejectedError("Персональні обмеження посилки не виконані");
        const allowed = await this.policy.isAllowed(customer, parcel, request.distanceKm);
        boolean(allowed);
        if (!allowed) throw new ShipmentRejectedError("Доставку не дозволено");
        const adjustment = 0;
        return await this.shipments.create(new Shipment({ id: 0, reference: request.reference,
            customerId: customer.id, parcelId: parcel.id, distanceKm: request.distanceKm,
            urgent: request.urgent, pickup: request.pickup, regular: customer.regular,
            weightGrams: parcel.weightGrams, declaredValue: parcel.declaredValue,
            lengthCm: parcel.lengthCm, widthCm: parcel.widthCm, heightCm: parcel.heightCm,
            baseFee, adjustment, total: baseFee + adjustment, refund: 0, status: ShipmentStatus.REGISTERED }));
    }
    async dispatch(id) { return this.changeStatus(id, ShipmentStatus.REGISTERED, ShipmentStatus.DISPATCHED); }
    async deliver(id) { return this.changeStatus(id, ShipmentStatus.DISPATCHED, ShipmentStatus.DELIVERED); }
    async cancel(id) { return this.changeStatus(id, ShipmentStatus.REGISTERED, ShipmentStatus.CANCELLED); }
    async changeStatus(id, expected, next) {
        integer(id, 1, Number.MAX_SAFE_INTEGER);
        const shipment = await this.shipments.findById(id);
        if (!shipment) throw new ResourceNotFoundError("Відправлення не знайдено");
        if (shipment.status !== expected) throw new ShipmentRejectedError("Перехід статусу заборонений");
        const refund = next === ShipmentStatus.CANCELLED ? this.calculateRefund(shipment.total) : shipment.refund;
        await this.shipments.updateStatus(id, expected, next, refund);
        return new Shipment({ ...shipment, status: next, refund });
    }
}
module.exports = { ShippingService, ShipmentRejectedError, ResourceNotFoundError };
