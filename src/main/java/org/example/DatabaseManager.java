package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    // URL для підключення до Railway MySQL
    private static final String URL = "jdbc:mysql://root:depHEcruGMsefEzPKkHJdOhwshshHJQn@gondola.proxy.rlwy.net:53947/railway";
    private static final String USER = "root"; // твій логін
    private static final String PASSWORD = "depHEcruGMsefEzPKkHJdOhwshshHJQn"; // твій пароль з Railway

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