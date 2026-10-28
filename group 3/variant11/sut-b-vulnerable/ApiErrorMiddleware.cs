using Npgsql;
namespace Rental;

public record ApiError(string Code, string Message);

public class ApiErrorMiddleware {
    private readonly RequestDelegate next;
    public ApiErrorMiddleware(RequestDelegate next) { this.next = next; }

    public async Task InvokeAsync(HttpContext context) {
        try { await next(context); }
        catch (Exception error) when (!context.Response.HasStarted) {
            if (error is NpgsqlException) {
                context.Response.StatusCode=500;
                await context.Response.WriteAsJsonAsync(new { code="DATABASE_ERROR", message=error.Message, trace=error.ToString() });
                return;
            }
            var (status, code, message) = error switch {
                ResourceNotFoundException => (404, "NOT_FOUND", error.Message),
                RentalRejectedException => (409, "RENTAL_CONFLICT", error.Message),
                RentalPolicyException => (502, "POLICY_UNAVAILABLE", "Зовнішня перевірка недоступна"),
                ArgumentException => (400, "INVALID_REQUEST", "Некоректні дані запиту"),
                PostgresException { SqlState: "23505" } => (409, "DUPLICATE_RESOURCE", "Ресурс із таким кодом уже існує"),
                PostgresException { SqlState: "23503" } => (409, "RESOURCE_CONFLICT", "Конфлікт пов’язаних даних"),
                PostgresException { SqlState: "23514" or "23502" or "22003" } => (400, "INVALID_REQUEST", "Некоректні дані ресурсу"),
                NpgsqlException => (500, "DATABASE_ERROR", "Помилка роботи з БД"),
                _ => (500, "INTERNAL_ERROR", "Внутрішня помилка сервера")
            };
            context.Response.Clear();
            context.Response.StatusCode = status;
            await context.Response.WriteAsJsonAsync(new ApiError(code, message));
        }
    }
}

public class LocalRentalPolicy : IRentalPolicy {
    public bool IsAllowed(Equipment equipment, int quantity, int days) => true;
}
