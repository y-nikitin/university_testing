"use strict";
function integer(value, min, max) {
    if (!Number.isSafeInteger(value) || value < min || value > max) throw new RangeError("Ціле число поза допустимими межами");
}
class EnrollmentRejectedError extends Error {}
module.exports = { integer, EnrollmentRejectedError };
