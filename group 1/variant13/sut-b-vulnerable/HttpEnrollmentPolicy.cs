using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Courses;

public sealed class EnrollmentPolicyException : Exception
{
    public EnrollmentPolicyException(string message, Exception? inner = null) : base(message, inner) { }
}

public sealed class HttpEnrollmentPolicy : IEnrollmentPolicy, IDisposable
{
    private readonly HttpClient client;
    private readonly Uri endpoint;

    public HttpEnrollmentPolicy(Uri baseUrl, TimeSpan timeout)
    {
        ArgumentNullException.ThrowIfNull(baseUrl);
        if (!baseUrl.IsAbsoluteUri || (baseUrl.Scheme != "http" && baseUrl.Scheme != "https")
            || string.IsNullOrEmpty(baseUrl.Host) || baseUrl.Port == 0
            || baseUrl.UserInfo.Length != 0 || baseUrl.AbsolutePath != "/"
            || baseUrl.Query.Length != 0 || baseUrl.Fragment.Length != 0)
            throw new ArgumentException("Потрібна базова HTTP/HTTPS-адреса без шляху та облікових даних", nameof(baseUrl));
        if (timeout <= TimeSpan.Zero || timeout.TotalMilliseconds > int.MaxValue)
            throw new ArgumentOutOfRangeException(nameof(timeout), "Таймаут має бути додатним і скінченним");
        endpoint = new Uri(baseUrl, "/enrollment-policy");
        client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = timeout };
    }

    public bool IsAllowed(Student student, Course course)
    {
        ArgumentNullException.ThrowIfNull(student);
        ArgumentNullException.ThrowIfNull(course);
        return CheckAsync(student, course).GetAwaiter().GetResult();
    }

    private async Task<bool> CheckAsync(Student student, Course course)
    {
        using var content = new StringContent(
            JsonSerializer.Serialize(new { studentId = student.Id, courseId = course.Id }), Encoding.UTF8, "application/json");
        try
        {
            using var response = await client.PostAsync(endpoint, content).ConfigureAwait(false);
            if (response.StatusCode != HttpStatusCode.OK)
                throw new EnrollmentPolicyException($"Політика зарахування повернула HTTP {(int)response.StatusCode}");
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync().ConfigureAwait(false));
            if (json.RootElement.ValueKind != JsonValueKind.Object
                || !json.RootElement.TryGetProperty("allowed", out var allowed)
                || (allowed.ValueKind != JsonValueKind.True && allowed.ValueKind != JsonValueKind.False))
                throw new EnrollmentPolicyException("Відповідь повинна містити логічне поле allowed");
            return allowed.GetBoolean();
        }
        catch (Exception ex) when (ex is HttpRequestException or OperationCanceledException or JsonException)
        {
            throw new EnrollmentPolicyException("Не вдалося отримати коректну відповідь політики зарахування", ex);
        }
    }

    public void Dispose() => client.Dispose();
}
