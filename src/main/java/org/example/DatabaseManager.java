package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String URL = "jdbc:mysql://crossover.proxy.rlwy.net:21254/railway?useUnicode=true&characterEncoding=UTF-8&connectTimeout=5000&socketTimeout=5000";
    private static final String USER = "root";
    private static final String PASSWORD = "ByZkOlzbofgNZSBVlPCdjayWsDBJfEc";

    private static Connection connection;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("✅ Database connected successfully!");
        } catch (Exception e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized Connection getConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                // Перепідключення лише якщо з’єднання мертве
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("🔄 Database reconnected!");
            }
        } catch (SQLException e) {
            throw new SQLException("❌ Не вдалося підключитися до БД", e);
        }
        return connection;
    }

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
