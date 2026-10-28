using System.Data.Common;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Rental;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls("http://127.0.0.1:" + (Environment.GetEnvironmentVariable("API_PORT") ?? "8082"));
builder.Services.AddControllers()
    .AddJsonOptions(options => options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter()));
builder.Services.Configure<ApiBehaviorOptions>(options => {
    options.InvalidModelStateResponseFactory = _ =>
        new BadRequestObjectResult(new ApiError("INVALID_REQUEST", "Некоректні дані запиту"));
});
builder.Services.AddScoped<DbConnection>(_ => Database.Connect());
builder.Services.AddScoped<IEquipmentRepository, EquipmentRepository>();
builder.Services.AddScoped<IRentalRepository, RentalRepository>();
builder.Services.AddScoped<RentalService>();
builder.Services.AddScoped<IRentalPolicy>(_ => {
    var url = Environment.GetEnvironmentVariable("RENTAL_POLICY_URL");
    return string.IsNullOrWhiteSpace(url)
        ? new LocalRentalPolicy()
        : new HttpRentalPolicy(new Uri(url), TimeSpan.FromSeconds(2));
});
var app = builder.Build();
app.UseMiddleware<ApiErrorMiddleware>();
app.UseStatusCodePages(async context => {
    var response = context.HttpContext.Response;
    await response.WriteAsJsonAsync(new ApiError(
        response.StatusCode == 404 ? "NOT_FOUND" : "METHOD_NOT_ALLOWED",
        response.StatusCode == 404 ? "Маршрут не знайдено" : "Метод не підтримується"));
});
var uiDirectory = Path.GetFullPath(Path.Combine(builder.Environment.ContentRootPath, "..", "sut-c-vulnerable"));
if (Directory.Exists(uiDirectory)) {
    var files = new Microsoft.Extensions.FileProviders.PhysicalFileProvider(uiDirectory);
    app.Lifetime.ApplicationStopped.Register(files.Dispose);
    app.UseDefaultFiles(new DefaultFilesOptions { FileProvider = files });
    app.UseStaticFiles(new StaticFileOptions { FileProvider = files });
}
app.MapControllers();
app.Run();

public partial class Program { }
