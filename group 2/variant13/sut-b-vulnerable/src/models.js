"use strict";
const ShipmentStatus = Object.freeze({ REGISTERED: "Registered", DISPATCHED: "Dispatched", DELIVERED: "Delivered", CANCELLED: "Cancelled" });
class Customer {
    constructor(id, code, regular) { Object.assign(this, { id, code, regular }); Object.freeze(this); }
}
class Parcel {
    constructor(id, code, weightGrams, declaredValue, lengthCm = 10, widthCm = 10, heightCm = 10) {
        Object.assign(this, { id, code, weightGrams, declaredValue, lengthCm, widthCm, heightCm }); Object.freeze(this);
    }
}
class Shipment {
    constructor({ id, reference, customerId, parcelId, distanceKm, urgent, pickup, regular, weightGrams,
        declaredValue, lengthCm, widthCm, heightCm, baseFee, adjustment, total, refund, status }) {
        Object.assign(this, { id, reference, customerId, parcelId, distanceKm, urgent, pickup, regular, weightGrams,
            declaredValue, lengthCm, widthCm, heightCm, baseFee, adjustment, total, refund, status }); Object.freeze(this);
    }
}
class ShipmentRequest {
    constructor(reference, customerId, parcelId, distanceKm, urgent = false, pickup = false) {
        Object.assign(this, { reference, customerId, parcelId, distanceKm, urgent, pickup }); Object.freeze(this);
    }
}
module.exports = { Customer, Parcel, Shipment, ShipmentRequest, ShipmentStatus };
