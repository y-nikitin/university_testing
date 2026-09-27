using System;
using Npgsql;
namespace Delivery;
public static class Database {
    public static NpgsqlConnection Connect() {
        var settings = new NpgsqlConnectionStringBuilder {
            Host = Env("DB_HOST", "localhost"), Port = int.Parse(Env("DB_PORT", "5432")),
            Database = Env("DB_NAME", "delivery_variant16_test"), Username = Env("DB_USER", "delivery_student"), Password = Env("DB_PASSWORD", "")
        };
        var connection = new NpgsqlConnection(settings.ConnectionString);
        try { connection.Open(); return connection; } catch { connection.Dispose(); throw; }
    }
    private static string Env(string key, string fallback) => Environment.GetEnvironmentVariable(key) ?? fallback;
}
