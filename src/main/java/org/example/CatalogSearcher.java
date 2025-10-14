package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

public class CatalogSearcher {
    private final Map<String, Object> catalogData;

    public CatalogSearcher() {
        this.catalogData = loadCatalog();
        System.out.println("✅ Catalog loaded: keys = " + catalogData.keySet());
    }

    // 🔹 Пошук для адмінки у плоскому списку "products:"
    public List<Map<String, Object>> searchByKeywordsAdmin(String keywords) {
        List<Map<String, Object>> results = new ArrayList<>();
        String[] parts = keywords.toLowerCase().split("\\s+");

        List<Map<String, Object>> products = (List<Map<String, Object>>) catalogData.get("products");
        if (products == null) return results;

        for (Map<String, Object> product : products) {
            String name = ((String) product.get("name")).toLowerCase();

            boolean allMatch = true;
            for (String part : parts) {
                if (!name.contains(part)) {
                    allMatch = false;
                    break;
                }
            }

            if (allMatch) {
                results.add(product);
            }
        }
        return results;
    }

    private Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("catalog.yml")) {
            if (input == null) {
                throw new IllegalStateException("Файл catalog.yml не знайдений у resources!");
            }
            return yaml.load(input);
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public List<Map<String, Object>> findProductsByName(String name) {
        if (catalogData == null || catalogData.isEmpty()) {
            System.out.println("⚠️ Catalog data is empty!");
            return new ArrayList<>();
        }

        if (name == null || name.trim().isEmpty()) {
            System.out.println("⚠️ Назва товару порожня!");
            return new ArrayList<>();
        }

        String query = name.trim().toLowerCase();
        List<Map<String, Object>> allProducts = new ArrayList<>();

        // 🔹 Товари з плоского списку
        Object productsObj = catalogData.get("products");
        if (productsObj instanceof List) {
            allProducts.addAll((List<Map<String, Object>>) productsObj);
        }

        // 🔹 Товари з каталогу рекурсивно
        Object catalogObj = catalogData.get("catalog");
        if (catalogObj instanceof List) {
            extractProductsFromCatalogForSearch((List<Map<String, Object>>) catalogObj, allProducts, query);
        }

        // 🔹 Фільтруємо товари за назвою
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> product : allProducts) {
            String productName = (String) product.get("name");
            if (productName != null && productName.toLowerCase().contains(query)) {
                found.add(product);
            }
        }

        if (found.isEmpty()) {
            System.out.println("❌ Товар не знайдено: " + name);
        } else {
            System.out.println("✅ Знайдено " + found.size() + " товарів для: " + name);
            for (Map<String, Object> p : found) {
                System.out.println("- " + p.get("name"));
            }
        }

        return found;
    }

    public List<Map<String, Object>> getFlatProducts() {
        List<Map<String, Object>> products = (List<Map<String, Object>>) catalogData.get("products");
        return products != null ? products : new ArrayList<>();
    }

    public void extractProductsFromCatalogForSearch(List<Map<String, Object>> groups, List<Map<String, Object>> foundProducts, String query) {
        for (Map<String, Object> group : groups) {
            // Додаємо товари з products
            if (group.containsKey("products")) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) group.get("products");
                for (Map<String, Object> p : products) {
                    String productName = (String) p.getOrDefault("name", "");
                    if (productName.toLowerCase().contains(query.toLowerCase())) {
                        Map<String, Object> copy = new HashMap<>(p);
                        copy.put("category", group.getOrDefault("name", ""));
                        foundProducts.add(copy);
                    }
                }
            }

            // Рекурсія для підгруп
            if (group.containsKey("subgroups")) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) group.get("subgroups");
                extractProductsFromCatalogForSearch(subgroups, foundProducts, query);
            }
        }
    }

    // 🔹 Отримати повний каталог (плоский варіант без категорій)
    public List<Map<String, Object>> getCatalog() {
        if (catalogData == null || catalogData.isEmpty()) {
            System.out.println("⚠️ Catalog is empty!");
            return Collections.emptyList();
        }

        if (catalogData.containsKey("catalog")) {
            System.out.println("📂 Found key: catalog");
            return (List<Map<String, Object>>) catalogData.get("catalog");
        }

        if (catalogData.containsKey("products")) {
            System.out.println("📦 Found key: products");
            return (List<Map<String, Object>>) catalogData.get("products");
        }

        System.out.println("❌ No catalog or products key found in YAML!");
        return Collections.emptyList();
    }

    // 🔹 Отримати список категорій
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");

        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                categories.add((String) cat.get("name"));
            }
        }
        return categories;
    }

    // 🔹 Отримати підкатегорії для категорії
    public List<String> getSubcategories(String categoryName) {
        List<String> subcategories = new ArrayList<>();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");

        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                if (cat.get("name").equals(categoryName)) {
                    List<Map<String, Object>> subgroups = (List<Map<String, Object>>) cat.get("subgroups");
                    if (subgroups != null) {
                        for (Map<String, Object> subgroup : subgroups) {
                            subcategories.add((String) subgroup.get("name"));
                        }
                    }
                }
            }
        }
        return subcategories;
    }

    // 🔹 Отримати товари з підкатегорії
    public List<Map<String, Object>> getProducts(String categoryName, String subcategoryName) {
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");

        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                if (cat.get("name").equals(categoryName)) {
                    List<Map<String, Object>> subgroups = (List<Map<String, Object>>) cat.get("subgroups");
                    if (subgroups != null) {
                        for (Map<String, Object> subgroup : subgroups) {
                            if (subgroup.get("name").equals(subcategoryName)) {
                                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                                if (products != null) {
                                    // Гарантуємо, що кожен продукт має ключ photo
                                    for (Map<String, Object> p : products) {
                                        p.putIfAbsent("photo", "");
                                    }
                                    return products;
                                } else {
                                    return new ArrayList<>(); // порожній список, якщо products відсутні
                                }
                            }
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}