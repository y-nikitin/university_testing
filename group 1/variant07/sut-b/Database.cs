using System;
using Npgsql;
namespace Courses;
public static class Database {
    public static NpgsqlConnection Connect() {
        var settings = new NpgsqlConnectionStringBuilder {
            Host = Env("DB_HOST", "localhost"), Port = int.Parse(Env("DB_PORT", "5432")),
            Database = Env("DB_NAME", "courses_variant07_test"), Username = Env("DB_USER", "courses_student"), Password = Env("DB_PASSWORD", "")
        };
        var connection = new NpgsqlConnection(settings.ConnectionString);
        try { connection.Open(); return connection; } catch { connection.Dispose(); throw; }
    }
    private static string Env(string key, string fallback) => Environment.GetEnvironmentVariable(key) ?? fallback;
}
