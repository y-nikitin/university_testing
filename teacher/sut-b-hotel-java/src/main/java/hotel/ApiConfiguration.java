package hotel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.time.Duration;

@Configuration
public class ApiConfiguration {
    @Bean
    BookingPolicy bookingPolicy(@Value("${BOOKING_POLICY_URL:}") String url) {
        return url.isBlank() ? (room, nights, guests) -> true
                : new HttpBookingPolicy(URI.create(url), Duration.ofSeconds(2));
    }
}
