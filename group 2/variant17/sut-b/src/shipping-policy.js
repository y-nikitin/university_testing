"use strict";
class ShippingPolicy {
    async isAllowed(customer, parcel, distanceKm) { throw new Error("Передайте реалізацію зовнішнього дозволу"); }
}
module.exports = { ShippingPolicy };
