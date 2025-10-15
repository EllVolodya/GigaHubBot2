package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String URL = "jdbc:mysql://shortline.proxy.rlwy.net:59768/railway";
    private static final String USER = "root";
    private static final String PASSWORD = "bNhtxmMdEfRGKAfbbLpwZzDOcbwXKfhG";

    // --- Підключення до бази
    public static void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Тестове підключення
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                if (conn != null) {
                    System.out.println("✅ Database connected successfully!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Database connection failed!");
        }
    }

    // --- Отримати нове з'єднання
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // --- Відключення (для сумісності)
    public static void disconnect() {
        // Нічого не робимо, бо getConnection() відкриває нове підключення кожного разу
        System.out.println("🔌 Disconnect not needed: connections auto-closed after use.");
    }
}