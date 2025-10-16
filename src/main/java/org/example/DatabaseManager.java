package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String URL = "jdbc:mysql://crossover.proxy.rlwy.net:21254/railway?useUnicode=true&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=5000";
    private static final String USER = "root";
    private static final String PASSWORD = "ByZkOlzbofgNZSBVlPCdjayWsDBJfEcP";

    private static Connection connection;

    // --- Підключення до бази
    public static void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✅ Database connected successfully!");
            } else {
                System.out.println("ℹ️ Database already connected.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Database connection failed!");
        }
    }

    // --- Отримати підключення (автоматично перепідключається, якщо закрите)
    public static Connection getConnection() throws SQLException {
        System.out.println("ℹ️ Attempting to get DB connection...");
        if (connection == null || connection.isClosed()) {
            System.out.println("ℹ️ Connection null or closed, connecting...");
            connect();
        }
        return connection;
    }

    // --- Відключення бази
    public static void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("🔌 Database disconnected.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
