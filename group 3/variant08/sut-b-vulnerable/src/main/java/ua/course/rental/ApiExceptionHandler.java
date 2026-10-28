package ua.course.rental;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.sql.SQLException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code, String message) {}

    private ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> missing(ResourceNotFoundException ex) {
        return error(404, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> route() { return error(404, "NOT_FOUND", "Маршрут не знайдено"); }

    @ExceptionHandler(RentalRejectedException.class)
    public ResponseEntity<ApiError> conflict(RentalRejectedException ex) {
        return error(409, "RENTAL_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Некоректний запит"); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405).allow(ex.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new))
                .body(new ApiError("METHOD_NOT_ALLOWED", "Метод не підтримується"));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<java.util.Map<String,String>> dataAccess(org.springframework.dao.DataAccessException ex) {
        var trace = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(trace));
        return ResponseEntity.status(500).body(java.util.Map.of("code","DATABASE_ERROR","message",ex.toString(),"trace",trace.toString()));
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiError> database(SQLException ex) {
        String state = ex.getSQLState();
        if ("23505".equals(state)) return error(409, "DUPLICATE_RESOURCE", "Ресурс із таким кодом уже існує");
        if ("23503".equals(state)) return error(409, "RESOURCE_CONFLICT", "Конфлікт пов’язаних даних");
        if ("23514".equals(state) || "23502".equals(state)) return error(400, "INVALID_REQUEST", "Некоректні дані ресурсу");
        return error(500, "DATABASE_ERROR", "Помилка роботи з БД");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> policy() { return error(502, "POLICY_UNAVAILABLE", "Зовнішня перевірка недоступна"); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected() { return error(500, "INTERNAL_ERROR", "Внутрішня помилка сервера"); }
}
