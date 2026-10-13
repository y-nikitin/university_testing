package ua.course.delivery;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfiguration {
    @Bean
    public ShippingPolicy shippingPolicy(@Value("${SHIPPING_POLICY_URL:}") String url) {
        return url.isBlank() ? (customer, parcel, distanceKm) -> true
                : new HttpShippingPolicy(URI.create(url), Duration.ofSeconds(2));
    }
}
