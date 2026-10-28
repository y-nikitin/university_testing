package ua.course.rental;
import java.sql.*;
public final class Database {
    private Database() {}
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "rental_variant06_test"), env("DB_USER", "rental_student"), env("DB_PASSWORD", ""));
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
}
