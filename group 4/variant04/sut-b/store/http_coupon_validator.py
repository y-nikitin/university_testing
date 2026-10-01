import http.client
import json
import math
from urllib.parse import urlsplit
from .models import Product


class CouponValidationError(RuntimeError):
    """Зовнішня HTTP-перевірка недоступна або повернула некоректну відповідь."""


class HttpCouponValidator:
    def __init__(self, base_url: str, timeout: float = 2):
        if not isinstance(base_url, str):
            raise ValueError("Адреса перевірки купона має бути рядком")
        try:
            address = urlsplit(base_url)
            port = address.port
            if (address.scheme not in ("http", "https") or not address.hostname
                    or address.username is not None or address.password is not None
                    or address.path not in ("", "/") or address.query or address.fragment
                    or (port is not None and not 1 <= port <= 65535)):
                raise ValueError("Некоректна адреса перевірки купона")
        except ValueError as error:
            raise ValueError("Потрібна базова HTTP(S)-адреса без шляху та параметрів") from error
        if type(timeout) not in (int, float) or not math.isfinite(timeout) or timeout <= 0:
            raise ValueError("Таймаут має бути додатним скінченним числом")
        self._address = address
        self._timeout = timeout

    def is_valid(self, product: Product, quantity: int) -> bool:
        payload = json.dumps({"productId": product.id, "quantity": quantity}).encode("utf-8")
        connection_type = http.client.HTTPSConnection if self._address.scheme == "https" else http.client.HTTPConnection
        connection = connection_type(self._address.hostname, self._address.port, timeout=self._timeout)
        try:
            connection.request("POST", "/coupon-validation", body=payload,
                               headers={"Content-Type": "application/json", "Accept": "application/json"})
            response = connection.getresponse()
            if response.status != 200:
                raise CouponValidationError(f"Помилка HTTP-перевірки купона: {response.status}")
            result = json.loads(response.read().decode("utf-8"))
            if not isinstance(result, dict) or type(result.get("valid")) is not bool:
                raise CouponValidationError("Відповідь перевірки купона має містити логічне поле valid")
            return result["valid"]
        except (OSError, http.client.HTTPException) as error:
            raise CouponValidationError("HTTP-перевірка недоступна або перевищено час очікування") from error
        except (UnicodeError, json.JSONDecodeError) as error:
            raise CouponValidationError("Некоректний JSON у відповіді перевірки купона") from error
        finally:
            connection.close()
