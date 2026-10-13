package ua.course.rental;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfiguration {
    @Bean
    public RentalPolicy rentalPolicy(@Value("${RENTAL_POLICY_URL:}") String url) {
        return url.isBlank() ? (equipment, quantity, days) -> true
                : new HttpRentalPolicy(URI.create(url), Duration.ofSeconds(2));
    }
}
