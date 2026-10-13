using System.Data.Common;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Delivery;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls("http://127.0.0.1:" + (Environment.GetEnvironmentVariable("API_PORT") ?? "8080"));
builder.Services.AddControllers()
    .AddJsonOptions(options => options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter()));
builder.Services.Configure<ApiBehaviorOptions>(options => {
    options.InvalidModelStateResponseFactory = _ =>
        new BadRequestObjectResult(new ApiError("INVALID_REQUEST", "Некоректні дані запиту"));
});
builder.Services.AddScoped<DbConnection>(_ => Database.Connect());
builder.Services.AddScoped<ICustomerRepository, CustomerRepository>();
builder.Services.AddScoped<IParcelRepository, ParcelRepository>();
builder.Services.AddScoped<IShipmentRepository, ShipmentRepository>();
builder.Services.AddScoped<ShippingService>();
builder.Services.AddScoped<IShippingPolicy>(_ => {
    var url = Environment.GetEnvironmentVariable("SHIPPING_POLICY_URL");
    return string.IsNullOrWhiteSpace(url)
        ? new LocalShippingPolicy()
        : new HttpShippingPolicy(new Uri(url), TimeSpan.FromSeconds(2));
});
var app = builder.Build();
app.UseMiddleware<ApiErrorMiddleware>();
app.UseStatusCodePages(async context => {
    var response = context.HttpContext.Response;
    await response.WriteAsJsonAsync(new ApiError(
        response.StatusCode == 404 ? "NOT_FOUND" : "METHOD_NOT_ALLOWED",
        response.StatusCode == 404 ? "Маршрут не знайдено" : "Метод не підтримується"));
});
app.MapControllers();
app.Run();

public partial class Program { }
