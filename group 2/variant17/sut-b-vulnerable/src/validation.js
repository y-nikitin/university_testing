"use strict";
function integer(value, min, max) {
    if (!Number.isSafeInteger(value) || value < min || value > max) throw new RangeError("Ціле число поза допустимими межами");
}
function boolean(value) {
    if (typeof value !== "boolean") throw new TypeError("Потрібна логічна ознака boolean");
}
function dimensions(lengthCm, widthCm, heightCm) {
    for (const side of [lengthCm, widthCm, heightCm]) integer(side, 1, 200);
}
class ShipmentRejectedError extends Error {}
module.exports = { integer, boolean, dimensions, ShipmentRejectedError };
