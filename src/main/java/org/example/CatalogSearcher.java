package org.example;

import java.sql.*;
import java.util.*;

import java.util.*;
import java.util.List;                       // для List
import java.util.Map;                        // для Map
import java.util.ArrayList;                  // для ArrayList

public class CatalogSearcher {

    public CatalogSearcher() {
        System.out.println("✅ CatalogSearcher ready to query DB.");
    }

    public List<Map<String, Object>> searchByKeywordsAdmin(String keywords) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (keywords == null || keywords.isEmpty()) return results;

        String sql = """
            SELECT p.*, s.name AS subcategory, c.name AS category
            FROM products p
            JOIN subcategories s ON p.subcategory_id = s.id
            JOIN categories c ON s.category_id = c.id
            WHERE LOWER(p.name) LIKE ?
            """;

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DatabaseManager.getConnection(); // ⚡ бере вже активне з’єднання
            ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + keywords.toLowerCase() + "%");

            rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> product = new HashMap<>();
                product.put("id", rs.getInt("id"));
                product.put("name", rs.getString("name"));
                product.put("price", rs.getString("price"));
                product.put("subcategory", rs.getString("subcategory"));
                product.put("category", rs.getString("category"));
                results.add(product);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error executing product search query: " + e.getMessage());
        } finally {
            // Закриваємо тільки statement і resultset — з’єднання залишається активним
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    // ---------------- Пошук по назві ----------------
    public List<Map<String, Object>> findProductsByName(String name) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) return results;

        String sql = """
            SELECT p.*, s.name AS subcategory, c.name AS category
            FROM products p
            JOIN subcategories s ON p.subcategory_id = s.id
            JOIN categories c ON s.category_id = c.id
            WHERE LOWER(p.name) LIKE ?
            ORDER BY p.name;
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + name.toLowerCase() + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) results.add(mapProduct(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    // ---------------- Категорії ----------------
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT name FROM categories ORDER BY id ASC";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) categories.add(rs.getString("name"));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return categories;
    }

    // ---------------- Підкатегорії ----------------
    public List<String> getSubcategories(String categoryName) {
        List<String> subcategories = new ArrayList<>();
        String sql = """
            SELECT s.name
            FROM subcategories s
            JOIN categories c ON s.category_id = c.id
            WHERE c.name = ?
            ORDER BY s.name;
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) subcategories.add(rs.getString("name"));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return subcategories;
    }

    // ---------------- Продукти ----------------
    public List<Map<String, Object>> getProducts(String categoryName, String subcategoryName) {
        List<Map<String, Object>> products = new ArrayList<>();
        String sql = """
            SELECT p.*, s.name AS subcategory, c.name AS category
            FROM products p
            JOIN subcategories s ON p.subcategory_id = s.id
            JOIN categories c ON s.category_id = c.id
            WHERE c.name = ? AND s.name = ?
            ORDER BY p.name;
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            stmt.setString(2, subcategoryName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) products.add(mapProduct(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return products;
    }

    // ---------------- Всі продукти ----------------
    public List<Map<String, Object>> getFlatProducts() {
        List<Map<String, Object>> products = new ArrayList<>();
        String sql = """
            SELECT p.*, s.name AS subcategory, c.name AS category
            FROM products p
            JOIN subcategories s ON p.subcategory_id = s.id
            JOIN categories c ON s.category_id = c.id
            ORDER BY p.name;
        """;

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) products.add(mapProduct(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return products;
    }

    // ---------------- Допоміжний метод ----------------
    private Map<String, Object> mapProduct(ResultSet rs) throws SQLException {
        Map<String, Object> product = new HashMap<>();
        product.put("id", rs.getInt("id"));
        product.put("name", rs.getString("name"));
        product.put("price", rs.getDouble("price"));
        product.put("unit", rs.getString("unit"));
        product.put("description", rs.getString("description"));
        product.put("photo", rs.getString("photo"));
        product.put("subcategory", rs.getString("subcategory"));
        product.put("category", rs.getString("category"));
        return product;
    }
}