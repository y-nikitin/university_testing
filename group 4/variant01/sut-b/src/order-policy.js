"use strict";
class OrderPolicy {
    async isAllowed(product, quantity) {
        throw new Error("Передайте реалізацію зовнішньої перевірки дозволу");
    }
}
module.exports = { OrderPolicy };
