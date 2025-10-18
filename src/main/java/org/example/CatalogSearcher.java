package org.example;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.sql.*;

import java.util.*;
import java.util.List;                       // для List
import java.util.Map;                        // для Map
import java.util.ArrayList;                  // для ArrayList

public class CatalogSearcher {

    private static final String CATALOG_PATH = "src/main/resources/catalog.yml";
    public CatalogSearcher() {
        System.out.println("✅ CatalogSearcher ready to query DB.");
    }


    public static List<Map<String, Object>> loadProducts() {
        List<Map<String, Object>> products = new ArrayList<>();

        try (InputStream input = new FileInputStream(CATALOG_PATH)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            if (data != null && data.containsKey("products")) {
                products = (List<Map<String, Object>>) data.get("products");
                System.out.println("✅ Loaded " + products.size() + " products from catalog.yml");
            } else {
                System.out.println("⚠️ catalog.yml is empty or missing 'products' key");
            }

        } catch (Exception e) {
            System.err.println("❌ Error loading catalog.yml: " + e.getMessage());
        }

        return products;
    }

    public List<Map<String, Object>> searchMixedFromYAML(String keyword) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 1️⃣ Завантаження catalog.yml через ClassLoader
        List<Map<String, Object>> yamlProducts = new ArrayList<>();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("catalog.yml")) {
            if (input == null) {
                System.err.println("❌ catalog.yml not found in resources!");
                return results;
            }

            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> data = yaml.load(input);

            if (data != null && data.containsKey("products")) {
                yamlProducts = (List<Map<String, Object>>) data.get("products");
                System.out.println("✅ Loaded " + yamlProducts.size() + " products from catalog.yml");
            } else {
                System.out.println("⚠️ catalog.yml is empty or missing 'products' key");
            }

        } catch (Exception e) {
            System.err.println("❌ Error loading catalog.yml: " + e.getMessage());
            e.printStackTrace();
        }

        // 2️⃣ Фільтруємо по ключовому слову
        yamlProducts.stream()
                .filter(p -> p.get("name").toString().toLowerCase().contains(keyword.toLowerCase()))
                .forEach(p -> {
                    String name = p.get("name").toString();
                    String price = p.get("price").toString();

                    // 3️⃣ Отримуємо категорію та підкатегорію з MySQL
                    String category = "❓";
                    String subcategory = "❓";

                    List<Map<String, Object>> dbMatches = findProductsByName(name);
                    if (!dbMatches.isEmpty()) {
                        Map<String, Object> match = dbMatches.get(0);
                        category = match.get("category") != null ? match.get("category").toString() : "❓";
                        subcategory = match.get("subcategory") != null ? match.get("subcategory").toString() : "❓";
                    }

                    // 4️⃣ Формуємо текст для Telegram
                    String formattedText = String.format("""
                        📦 %s
                        💰 Ціна: %s грн за шт
                        📂 %s → %s
                        """, name, price, category, subcategory);

                    // 5️⃣ Кладемо у Map для searchResults
                    Map<String, Object> productMap = new HashMap<>();
                    productMap.put("text", formattedText);
                    productMap.put("name", name);
                    productMap.put("price", price);
                    productMap.put("category", category);
                    productMap.put("subcategory", subcategory);

                    results.add(productMap);
                });

        return results;
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
        SELECT p.id, p.name, p.price, p.unit, p.description, p.photo, p.manufacturer,
               s.name AS subcategory, c.name AS category
        FROM products p
        JOIN subcategories s ON p.subcategory_id = s.id
        JOIN categories c ON s.category_id = c.id
        WHERE c.name = ? AND s.name = ?
        ORDER BY p.id;
    """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            stmt.setString(2, subcategoryName);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", rs.getInt("id"));
                    product.put("name", rs.getString("name"));
                    product.put("price", rs.getObject("price"));
                    product.put("unit", rs.getString("unit"));
                    product.put("description", rs.getString("description"));
                    product.put("photo", rs.getString("photo"));
                    product.put("subcategory", rs.getString("subcategory"));
                    product.put("category", rs.getString("category"));

                    // 🏭 Обробка manufacturer
                    Object manufacturerObj = rs.getObject("manufacturer");
                    String manufacturer = "";
                    if (manufacturerObj instanceof byte[] bytes) {
                        manufacturer = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    } else if (manufacturerObj != null) {
                        manufacturer = manufacturerObj.toString();
                    }
                    product.put("manufacturer", manufacturer);

                    products.add(product);

                    System.out.println("DEBUG: Loaded product '" + rs.getString("name") + "', manufacturer='" + manufacturer + "'");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ getProducts SQL error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("DEBUG: getProducts() -> found " + products.size() + " products for category=" + categoryName + ", subcategory=" + subcategoryName);
        return products;
    }

    public List<String> searchMixed(String keyword) {
        List<String> results = new ArrayList<>();

        try {
            // 1️⃣ Завантажуємо продукти з catalog.yml
            String catalogPath = "src/main/resources/catalog.yml";
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> data;

            try (InputStream input = new FileInputStream(catalogPath)) {
                data = yaml.load(input);
            }

            if (data == null || !data.containsKey("products")) {
                System.out.println("⚠️ catalog.yml is empty or missing 'products' key");
                return results;
            }

            List<Map<String, Object>> yamlProducts = (List<Map<String, Object>>) data.get("products");

            // 2️⃣ Фільтруємо по ключовому слову
            yamlProducts.stream()
                    .filter(p -> p.get("name").toString().toLowerCase().contains(keyword.toLowerCase()))
                    .forEach(p -> {
                        String name = p.get("name").toString();
                        String price = p.get("price").toString();

                        // 3️⃣ Отримуємо з MySQL підкатегорію/категорію
                        String category = "❓";
                        String subcategory = "❓";

                        List<Map<String, Object>> dbMatches = findProductsByName(name);
                        if (!dbMatches.isEmpty()) {
                            Map<String, Object> match = dbMatches.get(0);
                            category = match.get("category") != null ? match.get("category").toString() : "❓";
                            subcategory = match.get("subcategory") != null ? match.get("subcategory").toString() : "❓";
                        }

                        // 4️⃣ Формуємо текст результату
                        String formatted = String.format("""
                        📦 %s
                        💰 Ціна: %s грн за шт
                        📂 %s → %s
                        """, name, price, category, subcategory);

                        results.add(formatted);
                    });

        } catch (Exception e) {
            System.err.println("❌ Error in searchMixed: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
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