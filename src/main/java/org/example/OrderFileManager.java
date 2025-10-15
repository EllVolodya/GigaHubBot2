package org.example;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderFileManager {

    // --- Add a new order
    public static boolean addOrder(Map<String, Object> orderData) {
        System.out.println("[addOrder] Starting to add order: " + orderData);

        List<Map<String, Object>> cart = (List<Map<String, Object>>) orderData.get("items");
        StringBuilder itemsDb = new StringBuilder();
        double total = 0;

        if (cart != null) {
            for (Map<String, Object> item : cart) {
                String name = item.getOrDefault("name", item.getOrDefault("title", "Unnamed")).toString();
                Object priceObj = item.get("price");
                double price = 0;

                System.out.println("[addOrder] Raw priceObj = " + priceObj +
                        " (type=" + (priceObj == null ? "null" : priceObj.getClass().getSimpleName()) + ")");

                if (priceObj != null) {
                    String priceStr = priceObj.toString().trim();

                    // Try to extract first numeric value (e.g. "Ціна: 191 грн за шт" → 191)
                    Matcher matcher = Pattern.compile("([0-9]+([.,][0-9]+)?)").matcher(priceStr);
                    if (matcher.find()) {
                        String numeric = matcher.group(1).replace(",", ".");
                        try {
                            price = Double.parseDouble(numeric);
                        } catch (NumberFormatException e) {
                            System.err.println("[addOrder] Failed to parse numeric price: " + numeric);
                        }
                    } else {
                        System.err.println("[addOrder] No numeric value found in: " + priceStr);
                    }
                } else {
                    System.err.println("[addOrder] priceObj is null for item: " + name);
                }

                itemsDb.append(name).append(":").append(price).append(";");
                total += price;

                System.out.println("[addOrder] Parsed item: " + name + " | price=" + price);
            }
        }

        System.out.println("[addOrder] Generated itemsDb = " + itemsDb);
        System.out.println("[addOrder] Total amount = " + total);

        orderData.put("item", itemsDb.toString());
        orderData.put("total", total);

        String sql = """
                INSERT INTO orders 
                (orderCode, userId, deliveryType, city, address, postOffice, 
                fullName, phone, card, status, comment, total, item, date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, (String) orderData.get("orderCode"));
            stmt.setLong(2, Long.parseLong(orderData.get("userId").toString()));
            stmt.setString(3, (String) orderData.getOrDefault("deliveryType", null));
            stmt.setString(4, (String) orderData.getOrDefault("city", null));
            stmt.setString(5, (String) orderData.getOrDefault("address", null));
            stmt.setString(6, (String) orderData.getOrDefault("postOffice", null));
            stmt.setString(7, (String) orderData.getOrDefault("fullName", null));
            stmt.setString(8, (String) orderData.getOrDefault("phone", null));
            stmt.setString(9, (String) orderData.getOrDefault("card", null));
            stmt.setString(10, (String) orderData.getOrDefault("status", "New"));
            stmt.setString(11, (String) orderData.getOrDefault("comment", ""));
            stmt.setDouble(12, total);
            stmt.setString(13, itemsDb.toString());
            stmt.setDate(14, Date.valueOf(LocalDate.now()));

            int rows = stmt.executeUpdate();
            System.out.println("[addOrder] Rows inserted: " + rows);
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[addOrder] SQL error: " + e.getMessage());
            return false;
        }
    }

    // --- Update order status
    public static boolean updateOrderStatus(String orderCode, String status, String comment) {
        System.out.println("[updateOrderStatus] OrderCode: " + orderCode + ", Status: " + status + ", Comment: " + comment);
        String sql = "UPDATE orders SET status = ?, comment = ? WHERE orderCode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, comment);
            stmt.setString(3, orderCode);
            int updated = stmt.executeUpdate();
            System.out.println("[updateOrderStatus] Rows updated: " + updated);
            return updated > 0;
        } catch (SQLException e) {
            System.err.println("[updateOrderStatus] SQL error: " + e.getMessage());
            return false;
        }
    }

    // --- Delete order
    public static boolean deleteOrder(String orderCode) {
        System.out.println("[deleteOrder] Deleting order: " + orderCode);
        String sql = "DELETE FROM orders WHERE orderCode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, orderCode);
            int deleted = stmt.executeUpdate();
            System.out.println("[deleteOrder] Rows deleted: " + deleted);
            return deleted > 0;
        } catch (SQLException e) {
            System.err.println("[deleteOrder] SQL error: " + e.getMessage());
            return false;
        }
    }

    // --- Get all orders
    public static List<Map<String, Object>> getOrders() {
        System.out.println("[getOrders] Loading all orders...");
        List<Map<String, Object>> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders ORDER BY id DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("id", rs.getInt("id"));
                order.put("orderCode", rs.getString("orderCode"));
                order.put("userId", rs.getLong("userId"));
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
                order.put("item", rs.getString("item"));

                System.out.println("[getOrders] Fetched order: code=" + order.get("orderCode")
                        + ", total=" + order.get("total")
                        + ", item=" + order.get("item"));

                orders.add(order);
            }

            System.out.println("[getOrders] Total orders loaded: " + orders.size());

        } catch (SQLException e) {
            System.err.println("[getOrders] SQL error: " + e.getMessage());
        }
        return orders;
    }
}
