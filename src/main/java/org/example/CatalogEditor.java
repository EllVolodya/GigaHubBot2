package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
            if (rs.next()) return false; // Категорія вже існує

            insertStmt.setString(1, categoryName);
            insertStmt.executeUpdate();
            System.out.println("✅ Категорію додано: " + categoryName);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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

            // Знайти категорію
            findCategoryStmt.setString(1, categoryName);
            ResultSet categoryRs = findCategoryStmt.executeQuery();
            if (!categoryRs.next()) return false;
            int categoryId = categoryRs.getInt("id");

            // Перевірити, чи вже існує підкатегорія
            checkStmt.setString(1, subcategoryName);
            checkStmt.setInt(2, categoryId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) return false;

            // Додати підкатегорію
            insertStmt.setString(1, subcategoryName);
            insertStmt.setInt(2, categoryId);
            insertStmt.executeUpdate();

            System.out.println("✅ Підкатегорію '" + subcategoryName + "' додано до '" + categoryName + "'");
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Додати товар у підкатегорію
    public static boolean addProductToSubcategory(String productName, String price, String subcategoryName) {
        String findSubSql = "SELECT id FROM subcategories WHERE name = ?";
        String checkProductSql = "SELECT id FROM products WHERE name = ? AND subcategory_id = ?";
        String insertSql = """
                INSERT INTO products (name, price, unit, description, photo, created_at, subcategory_id)
                VALUES (?, ?, 'шт', '', '', CURRENT_DATE, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement findSubStmt = conn.prepareStatement(findSubSql);
             PreparedStatement checkStmt = conn.prepareStatement(checkProductSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // Знайти підкатегорію
            findSubStmt.setString(1, subcategoryName);
            ResultSet subRs = findSubStmt.executeQuery();
            if (!subRs.next()) return false;
            int subcategoryId = subRs.getInt("id");

            // Перевірити, чи товар уже є
            checkStmt.setString(1, productName);
            checkStmt.setInt(2, subcategoryId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) return false;

            // Додати товар
            insertStmt.setString(1, productName);
            insertStmt.setString(2, price);
            insertStmt.setInt(3, subcategoryId);
            insertStmt.executeUpdate();

            System.out.println("✅ Товар '" + productName + "' додано у підкатегорію '" + subcategoryName + "'");
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Оновити виробника
    public static boolean updateProductManufacturer(String productName, String manufacturer) {
        String sql = "UPDATE products SET manufacturer = ? WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, manufacturer);
            stmt.setString(2, productName);
            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Допоміжний метод: обмеження оновлення лише дозволених полів
    private static boolean isAllowedField(String field) {
        return field.equals("price") || field.equals("description") || field.equals("photo") || field.equals("unit");
    }

    // --- Перевірка існування категорії
    public static boolean categoryExists(String categoryName) {
        String sql = "SELECT id FROM categories WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categoryName);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Оновити будь-яке поле товару
    public static boolean updateField(String productName, String field, Object value) {
        if (!isAllowedField(field)) {
            System.out.println("❌ Заборонене поле: " + field);
            return false;
        }

        String sql = "UPDATE products SET " + field + " = ? WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, value);
            stmt.setString(2, productName);
            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Отримати ціну продукту з YAML
    public static String getProductPriceFromYAML(String productName) {
        try (InputStream inputStream = CatalogEditor.class.getClassLoader().getResourceAsStream("products.yaml")) {
            if (inputStream == null) return "0";

            Yaml yaml = new Yaml();
            List<Map<String, Object>> products = yaml.load(inputStream);

            for (Map<String, Object> product : products) {
                String name = (String) product.get("name");
                if (name != null && name.equalsIgnoreCase(productName)) {
                    Object priceObj = product.get("price");
                    return priceObj != null ? priceObj.toString() : "0";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "0"; // дефолтна ціна, якщо продукт не знайдено
    }
}