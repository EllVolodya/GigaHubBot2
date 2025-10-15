package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    // URL для підключення до Railway MySQL
    private static final String URL = "jdbc:mysql://root:bNhtxmMdEfRGKAfbbLpwZzDOcbwXKfhG@shortline.proxy.rlwy.net:59768/railway";
    private static final String USER = "root"; // твій логін
    private static final String PASSWORD = "bNhtxmMdEfRGKAfbbLpwZzDOcbwXKfhG"; // твій пароль з Railway

    private static Connection connection;

    public static void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✅ Database connected successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Database connection failed!");
        }
    }

    public static boolean insertProduct(String name, String price, String unit, String description, String photo) {
        String sql = "INSERT INTO products (name, price, unit, description, photo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, price);
            stmt.setString(3, unit);
            stmt.setString(4, description);
            stmt.setString(5, photo);
            stmt.executeUpdate();

            System.out.println("✅ Товар '" + name + "' додано у базу даних!");
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Помилка при додаванні товару '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public static Connection getConnection() {
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