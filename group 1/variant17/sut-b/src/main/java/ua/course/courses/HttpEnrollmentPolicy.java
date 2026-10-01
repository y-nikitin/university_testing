package ua.course.courses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.io.IOException;

public final class HttpEnrollmentPolicy implements EnrollmentPolicy {
    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    public HttpEnrollmentPolicy(URI baseUrl, Duration timeout) {
        Objects.requireNonNull(baseUrl);
        if ((!"http".equals(baseUrl.getScheme()) && !"https".equals(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || (!baseUrl.getPath().isEmpty() && !baseUrl.getPath().equals("/"))
                || baseUrl.getPort() == 0 || baseUrl.getPort() > 65535)
            throw new IllegalArgumentException("Потрібна HTTP-адреса політики");
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("Таймаут має бути додатним");
        this.endpoint = baseUrl.resolve("/enrollment-policy");
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean isAllowed(Student student, Course course) {
        try {
            String body = json.writeValueAsString(Map.of("studentId", student.id(), "courseId", course.id()));
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Помилка HTTP-політики: " + response.statusCode());
            var result = json.readTree(response.body());
            if (result == null || !result.isObject() || !result.path("allowed").isBoolean())
                throw new IllegalStateException("Некоректна відповідь HTTP-політики");
            return result.get("allowed").booleanValue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP-перевірку перервано", error);
        } catch (IOException error) {
            throw new IllegalStateException("HTTP-перевірка недоступна", error);
        }
    }
}
