package ua.course.courses;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfiguration {
    @Bean
    public EnrollmentPolicy enrollmentPolicy(@Value("${ENROLLMENT_POLICY_URL:}") String url) {
        return url.isBlank() ? (student, course) -> true
                : new HttpEnrollmentPolicy(URI.create(url), Duration.ofSeconds(2));
    }
}
