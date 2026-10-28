using System;
using Npgsql;
namespace Delivery;
public static class Database {
    public static NpgsqlConnection Connect() {
        var settings = new NpgsqlConnectionStringBuilder {
            Host = Env("SECURITY_DB_HOST", "localhost"), Port = int.Parse(Env("SECURITY_DB_PORT", "55433")),
            Database = Env("SECURITY_DB_NAME", "delivery_variant16_security"), Username = Env("SECURITY_DB_USER", "delivery_student"), Password = Env("SECURITY_DB_PASSWORD", "")
        };
        var connection = new NpgsqlConnection(settings.ConnectionString);
        try { connection.Open(); return connection; } catch { connection.Dispose(); throw; }
    }
    private static string Env(string key, string fallback) => Environment.GetEnvironmentVariable(key) ?? fallback;
}
