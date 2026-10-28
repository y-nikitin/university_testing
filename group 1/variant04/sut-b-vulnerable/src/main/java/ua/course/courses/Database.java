package ua.course.courses;
import java.sql.*;
public final class Database {
    private Database() {}
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "courses_variant04_test"), env("DB_USER", "courses_student"), env("DB_PASSWORD", ""));
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
}
