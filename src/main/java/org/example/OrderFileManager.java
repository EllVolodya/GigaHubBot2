package org.example;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class OrderFileManager {

    // --- Додати нове замовлення
    public static boolean addOrder(Map<String, Object> orderData) {
        // Генеруємо рядок item для БД і рахуємо total
        List<Map<String, Object>> cart = (List<Map<String, Object>>) orderData.get("items");
        StringBuilder itemsDb = new StringBuilder();
        double total = 0;
        if (cart != null) {
            for (Map<String, Object> item : cart) {
                String name = item.getOrDefault("name", item.getOrDefault("title", "Без назви")).toString();
                double price = Double.parseDouble(item.getOrDefault("price", "0").toString());
                itemsDb.append(name).append(":").append(price).append(";");
                total += price;
            }
        }

        orderData.put("item", itemsDb.toString()); // для TEXT колонки
        orderData.put("total", total);             // підрахунок total

        String sql = "INSERT INTO orders " +
                "(orderCode, userId, deliveryType, city, address, postOffice, " +
                "fullName, phone, card, status, comment, total, item, date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, (String) orderData.get("orderCode"));
            stmt.setInt(2, (Integer) orderData.get("userId"));
            stmt.setString(3, (String) orderData.getOrDefault("deliveryType", null));
            stmt.setString(4, (String) orderData.getOrDefault("city", null));
            stmt.setString(5, (String) orderData.getOrDefault("address", null));
            stmt.setString(6, (String) orderData.getOrDefault("postOffice", null));
            stmt.setString(7, (String) orderData.getOrDefault("fullName", null));
            stmt.setString(8, (String) orderData.getOrDefault("phone", null));
            stmt.setString(9, (String) orderData.getOrDefault("card", null));
            stmt.setString(10, (String) orderData.getOrDefault("status", "Нове"));
            stmt.setString(11, (String) orderData.getOrDefault("comment", ""));
            stmt.setDouble(12, total);               // підрахований total
            stmt.setString(13, itemsDb.toString());  // TEXT item
            stmt.setDate(14, Date.valueOf(LocalDate.now()));

            stmt.executeUpdate();
            System.out.println("✅ Нове замовлення додано: " + orderData.get("orderCode"));
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Помилка при додаванні замовлення: " + e.getMessage());
            return false;
        }
    }

    // --- Оновити статус замовлення
    public static boolean updateOrderStatus(String orderCode, String status, String comment) {
        String sql = "UPDATE orders SET status = ?, comment = ? WHERE orderCode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setString(2, comment);
            stmt.setString(3, orderCode);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                System.out.println("✅ Статус оновлено для замовлення: " + orderCode);
                return true;
            } else {
                System.out.println("⚠️ Замовлення не знайдено: " + orderCode);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ Помилка при оновленні статусу: " + e.getMessage());
            return false;
        }
    }

    // --- Видалити замовлення
    public static boolean deleteOrder(String orderCode) {
        String sql = "DELETE FROM orders WHERE orderCode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, orderCode);
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                System.out.println("🗑️ Замовлення видалено: " + orderCode);
                return true;
            } else {
                System.out.println("⚠️ Замовлення не знайдено: " + orderCode);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("❌ Помилка при видаленні: " + e.getMessage());
            return false;
        }
    }

    // --- Отримати всі замовлення
    public static List<Map<String, Object>> getOrders() {
        List<Map<String, Object>> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders ORDER BY id DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("id", rs.getInt("id"));
                order.put("orderCode", rs.getString("orderCode"));
                order.put("userId", rs.getInt("userId"));
                order.put("deliveryType", rs.getString("deliveryType"));
                order.put("city", rs.getString("city"));
                order.put("address", rs.getString("address"));
                order.put("postOffice", rs.getString("postOffice"));
                order.put("fullName", rs.getString("fullName"));
                order.put("phone", rs.getString("phone"));
                order.put("card", rs.getString("card"));
                order.put("status", rs.getString("status"));
                order.put("comment", rs.getString("comment"));
                order.put("total", rs.getDouble("total"));
                order.put("date", rs.getDate("date"));
                order.put("item", rs.getString("item")); // для адмін-повідомлень
                orders.add(order);
            }

            System.out.println("📦 Завантажено замовлень: " + orders.size());
        } catch (SQLException e) {
            System.err.println("❌ Помилка при завантаженні замовлень: " + e.getMessage());
        }

        return orders;
    }
}
