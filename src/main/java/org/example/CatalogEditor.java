package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class CatalogEditor {

    // --- Додати категорію
    public static boolean addCategory(String categoryName) {
        String checkSql = "SELECT id FROM categories WHERE name = ?";
        String insertSql = "INSERT INTO categories (name) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            checkStmt.setString(1, categoryName);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) return false;

            insertStmt.setString(1, categoryName);
            insertStmt.executeUpdate();
            System.out.println("✅ Категорію додано: " + categoryName);
            return true;

        } catch (SQLException e) {
            System.err.println("❌ addCategory error: " + e.getMessage());
            return false;
        }
    }

    // --- Видалити категорію
    public static boolean deleteCategory(String categoryName) {
        String sql = "DELETE FROM categories WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ deleteCategory error: " + e.getMessage());
            return false;
        }
    }

    // --- Додати підкатегорію
    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        String findCategorySql = "SELECT id FROM categories WHERE name = ?";
        String checkSubSql = "SELECT id FROM subcategories WHERE name = ? AND category_id = ?";
        String insertSql = "INSERT INTO subcategories (name, category_id) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement findCategoryStmt = conn.prepareStatement(findCategorySql);
             PreparedStatement checkStmt = conn.prepareStatement(checkSubSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            findCategoryStmt.setString(1, categoryName);
            ResultSet categoryRs = findCategoryStmt.executeQuery();
            if (!categoryRs.next()) return false;
            int categoryId = categoryRs.getInt("id");

            checkStmt.setString(1, subcategoryName);
            checkStmt.setInt(2, categoryId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) return false;

            insertStmt.setString(1, subcategoryName);
            insertStmt.setInt(2, categoryId);
            insertStmt.executeUpdate();

            System.out.println("✅ Підкатегорію '" + subcategoryName + "' додано до '" + categoryName + "'");
            return true;

        } catch (SQLException e) {
            System.err.println("❌ addSubcategory error: " + e.getMessage());
            return false;
        }
    }

    // --- Додати товар у підкатегорію
    public static boolean addProductToSubcategory(String productName, double price, String subcategoryName) {
        String findSubSql = "SELECT id FROM subcategories WHERE name = ?";
        String checkProductSql = "SELECT id FROM products WHERE name = ? AND subcategory_id = ?";
        String insertSql = """
            INSERT INTO products (name, price, unit, description, photo, created_at, subcategory_id)
            VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement findSubStmt = conn.prepareStatement(findSubSql);
             PreparedStatement checkStmt = conn.prepareStatement(checkProductSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            findSubStmt.setString(1, subcategoryName);
            ResultSet subRs = findSubStmt.executeQuery();
            if (!subRs.next()) {
                System.out.println("❌ Підкатегорію '" + subcategoryName + "' не знайдено");
                return false;
            }
            int subcategoryId = subRs.getInt("id");

            checkStmt.setString(1, productName);
            checkStmt.setInt(2, subcategoryId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                System.out.println("⚠️ Товар '" + productName + "' вже існує");
                return false;
            }

            insertStmt.setString(1, productName);
            insertStmt.setDouble(2, price);
            insertStmt.setString(3, "шт");
            insertStmt.setString(4, "");
            insertStmt.setString(5, "");
            insertStmt.setInt(6, subcategoryId);

            insertStmt.executeUpdate();
            System.out.println("✅ Додано товар '" + productName + "' у '" + subcategoryName + "'");
            return true;

        } catch (SQLException e) {
            System.err.println("❌ addProductToSubcategory error: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateProductManufacturer(String productName, String manufacturer) {
        if (productName == null || productName.trim().isEmpty()) {
            System.out.println("❌ Назва продукту пуста");
            return false;
        }

        productName = productName.trim();
        boolean clearManufacturer = manufacturer == null
                || manufacturer.trim().isEmpty()
                || manufacturer.equalsIgnoreCase("❌");

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(true);

            // --- Дебаг: перевіримо, чи рядок взагалі існує ---
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT name FROM products WHERE LOWER(name) = LOWER(?)"
            )) {
                checkStmt.setString(1, productName);
                ResultSet rs = checkStmt.executeQuery();
                if (!rs.next()) {
                    System.out.println("⚠️ DEBUG: Рядок з назвою '" + productName + "' не знайдено у базі.");
                    return false;
                } else {
                    System.out.println("DEBUG: Рядок знайдено у базі: '" + rs.getString("name") + "'");
                }
            }

            // --- Основне оновлення ---
            String sql = clearManufacturer
                    ? "UPDATE products SET manufacturer = NULL WHERE LOWER(name) = LOWER(?)"
                    : "UPDATE products SET manufacturer = ? WHERE LOWER(name) = LOWER(?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (clearManufacturer) {
                    stmt.setString(1, productName);
                    System.out.println("DEBUG: Setting parameter 1 -> productName='" + productName + "'");
                } else {
                    byte[] manufacturerBytes = manufacturer.trim().getBytes(StandardCharsets.UTF_8);
                    stmt.setBytes(1, manufacturerBytes);
                    stmt.setString(2, productName);
                    System.out.println("DEBUG: Setting parameter 1 -> manufacturer='" + manufacturer + "'");
                    System.out.println("DEBUG: Setting parameter 2 -> productName='" + productName + "'");
                }

                int rows = stmt.executeUpdate();
                System.out.println("DEBUG: Rows affected = " + rows);

                if (rows == 0) {
                    System.out.println("⚠️ Товар '" + productName + "' не вдалося оновити.");
                    return false;
                }

                if (clearManufacturer) {
                    System.out.println("✅ Виробника видалено для товару: " + productName);
                } else {
                    System.out.println("✅ Виробника збережено для товару: " + productName);
                }

                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ updateProductManufacturer SQL error: " + e.getMessage());
            return false;
        }
    }

    // --- Оновити будь-яке поле товару
    public static boolean updateField(String productName, String field, Object value) {
        if (productName == null || productName.trim().isEmpty()) {
            System.out.println("❌ Назва продукту пуста");
            return false;
        }

        productName = productName.trim();
        System.out.println("DEBUG: Updating field '" + field + "' for product '" + productName + "' with value '" + value + "'");

        boolean isBlobField = "manufacturer".equals(field);
        boolean isAllowed = isBlobField || field.equals("price") || field.equals("description")
                || field.equals("photo") || field.equals("unit");

        if (!isAllowed) {
            System.out.println("❌ Заборонене поле: " + field);
            return false;
        }

        String sql = "UPDATE products SET " + field + " = ? WHERE LOWER(name) = LOWER(?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(true);

            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT name FROM products WHERE LOWER(name) = LOWER(?)"
            )) {
                checkStmt.setString(1, productName);
                ResultSet rs = checkStmt.executeQuery();
                if (!rs.next()) {
                    System.out.println("⚠️ DEBUG: Рядок з назвою '" + productName + "' не знайдено у базі.");
                    return false;
                } else {
                    System.out.println("DEBUG: Рядок знайдено у базі: '" + rs.getString("name") + "'");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isBlobField) {
                    // BLOB підтримка
                    if (value == null || value.toString().trim().isEmpty() || value.toString().equalsIgnoreCase("❌")) {
                        stmt.setNull(1, Types.BLOB);
                        System.out.println("DEBUG: Setting manufacturer to NULL");
                    } else {
                        byte[] bytes = value.toString().trim().getBytes(StandardCharsets.UTF_8);
                        stmt.setBytes(1, bytes);
                        System.out.println("DEBUG: Setting manufacturer bytes -> " + value);
                    }
                } else {
                    // Інші поля (price, description, unit, photo)
                    stmt.setObject(1, value);
                    System.out.println("DEBUG: Setting parameter 1 -> " + value);
                }

                stmt.setString(2, productName);
                System.out.println("DEBUG: Setting parameter 2 -> productName='" + productName + "'");

                int rows = stmt.executeUpdate();
                System.out.println("DEBUG: Rows affected = " + rows);

                if (rows == 0) {
                    System.out.println("⚠️ Не вдалося оновити поле '" + field + "' для товару '" + productName + "'");
                    return false;
                }

                System.out.println("✅ Поле '" + field + "' успішно оновлено для товару '" + productName + "'");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ updateField SQL error: " + e.getMessage());
            return false;
        }
    }

    // --- Отримати будь-яке поле товару у вигляді String
    public static String getField(String productName, String field) {
        if (!isAllowedField(field) && !field.equals("manufacturer")) {
            System.out.println("❌ Заборонене поле: " + field);
            return null;
        }

        String sql = "SELECT " + field + " FROM products WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, productName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Object value = rs.getObject(field);
                return value != null ? value.toString() : null;
            }
            return null;

        } catch (SQLException e) {
            System.err.println("❌ getField error: " + e.getMessage());
            return null;
        }
    }

    // --- Перевірити існування категорії
    public static boolean categoryExists(String categoryName) {
        String sql = "SELECT id FROM categories WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("❌ categoryExists error: " + e.getMessage());
            return false;
        }
    }

    // --- Перевірити існування підкатегорії
    public static boolean subcategoryExists(String subcategoryName) {
        String sql = "SELECT id FROM subcategories WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, subcategoryName);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("❌ subcategoryExists error: " + e.getMessage());
            return false;
        }
    }

    // --- Отримати ціну продукту з YAML
    public static double getProductPriceFromYAML(String productName) {
        try (InputStream inputStream = CatalogEditor.class.getClassLoader().getResourceAsStream("catalog.yml")) {
            if (inputStream == null) return 0.0;

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            Object productsObj = data.get("products");
            if (!(productsObj instanceof List<?> products)) return 0.0;

            for (Object obj : products) {
                if (obj instanceof Map<?, ?> product) {
                    Object nameObj = product.get("name");
                    if (nameObj == null) continue;

                    String name = nameObj.toString().trim();
                    if (name.equalsIgnoreCase(productName.trim())) {
                        Object priceObj = product.get("price");
                        if (priceObj != null) {
                            return Double.parseDouble(priceObj.toString().replace(",", ".").trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ getProductPriceFromYAML error: " + e.getMessage());
        }
        return 0.0;
    }

    // --- Дозволені поля
    private static boolean isAllowedField(String field) {
        return switch (field) {
            case "price", "description", "photo", "unit" -> true;
            default -> false;
        };
    }
}
