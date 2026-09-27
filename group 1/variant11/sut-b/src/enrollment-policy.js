"use strict";
class EnrollmentPolicy {
    async isAllowed(student, course) { throw new Error("Передайте реалізацію зовнішнього дозволу"); }
}
module.exports = { EnrollmentPolicy };
