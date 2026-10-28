package ua.course.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.time.Duration;

@Configuration
public class PolicyConfiguration {
    @Bean
    OrderPolicy orderPolicy(@Value("${ORDER_POLICY_URL:}") String url) {
        return url.isBlank() ? (product, quantity) -> true
                : new HttpOrderPolicy(URI.create(url), Duration.ofSeconds(2));
    }
}
