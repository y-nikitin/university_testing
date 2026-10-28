using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Delivery;

public sealed class ShippingPolicyException : Exception
{
    public ShippingPolicyException(string message, Exception? inner = null) : base(message, inner) { }
}

public sealed class HttpShippingPolicy : IShippingPolicy, IDisposable
{
    private readonly HttpClient client;
    private readonly Uri endpoint;

    public HttpShippingPolicy(Uri baseUrl, TimeSpan timeout)
    {
        ArgumentNullException.ThrowIfNull(baseUrl);
        if (!baseUrl.IsAbsoluteUri || (baseUrl.Scheme != "http" && baseUrl.Scheme != "https")
            || string.IsNullOrEmpty(baseUrl.Host) || baseUrl.Port == 0
            || baseUrl.UserInfo.Length != 0 || baseUrl.AbsolutePath != "/"
            || baseUrl.Query.Length != 0 || baseUrl.Fragment.Length != 0)
            throw new ArgumentException("Потрібна базова HTTP/HTTPS-адреса без шляху та облікових даних", nameof(baseUrl));
        if (timeout <= TimeSpan.Zero || timeout.TotalMilliseconds > int.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(timeout), "Таймаут має бути додатним і скінченним");
        endpoint = new Uri(baseUrl, "/shipping-policy");
        client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = timeout };
    }

    public bool IsAllowed(Customer customer, Parcel parcel, int distanceKm)
    {
        ArgumentNullException.ThrowIfNull(customer);
        ArgumentNullException.ThrowIfNull(parcel);
        return CheckAsync(customer, parcel, distanceKm).GetAwaiter().GetResult();
    }

    private async Task<bool> CheckAsync(Customer customer, Parcel parcel, int distanceKm)
    {
        using var content = new StringContent(
            JsonSerializer.Serialize(new { customerId = customer.Id, parcelId = parcel.Id, distanceKm }), Encoding.UTF8, "application/json");
        try
        {
            using var response = await client.PostAsync(endpoint, content).ConfigureAwait(false);
            if (response.StatusCode != HttpStatusCode.OK)
                throw new ShippingPolicyException($"Політика доставки повернула HTTP {(int)response.StatusCode}");
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync().ConfigureAwait(false));
            if (json.RootElement.ValueKind != JsonValueKind.Object
                || !json.RootElement.TryGetProperty("allowed", out var allowed)
                || (allowed.ValueKind != JsonValueKind.True && allowed.ValueKind != JsonValueKind.False))
                throw new ShippingPolicyException("Відповідь повинна містити логічне поле allowed");
            return allowed.GetBoolean();
        }
        catch (Exception ex) when (ex is HttpRequestException or OperationCanceledException or JsonException)
        {
            throw new ShippingPolicyException("Не вдалося отримати коректну відповідь політики доставки", ex);
        }
    }

    public void Dispose() => client.Dispose();
}
