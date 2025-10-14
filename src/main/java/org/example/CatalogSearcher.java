package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.FileInputStream;
import java.util.*;

public class CatalogSearcher {

    private static final String CATALOG_PATH = "/app/catalog.yml"; // ✅ файл у Railway app
    private final Map<String, Object> catalogData;

    public CatalogSearcher() {
        this.catalogData = loadCatalog();
        System.out.println("✅ Catalog loaded: keys = " + catalogData.keySet());
    }

    // 🔹 Завантажує catalog.yml з /app
    private Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        try (InputStream input = new FileInputStream(CATALOG_PATH)) {
            Object loaded = yaml.load(input);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            } else {
                return new HashMap<>();
            }
        } catch (Exception e) {
            throw new IllegalStateException("❌ catalog.yml не знайдений за шляхом: " + CATALOG_PATH, e);
        }
    }

    // 🔹 Пошук товарів для адмінки у плоскому списку "products:"
    public List<Map<String, Object>> searchByKeywordsAdmin(String keywords) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (keywords == null || keywords.isEmpty()) return results;

        String[] parts = keywords.toLowerCase().split("\\s+");
        List<Map<String, Object>> products = getFlatProducts();

        for (Map<String, Object> product : products) {
            String name = Objects.toString(product.get("name"), "").toLowerCase();
            boolean allMatch = Arrays.stream(parts).allMatch(name::contains);
            if (allMatch) results.add(product);
        }

        return results;
    }

    // 🔹 Повертає плоский список товарів
    public List<Map<String, Object>> getFlatProducts() {
        List<Map<String, Object>> products = (List<Map<String, Object>>) catalogData.get("products");
        return products != null ? products : new ArrayList<>();
    }

    // 🔹 Пошук товарів за назвою у всьому catalog
    public List<Map<String, Object>> findProductsByName(String name) {
        List<Map<String, Object>> found = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) return found;

        String query = name.trim().toLowerCase();
        List<Map<String, Object>> allProducts = new ArrayList<>();
        Object productsObj = catalogData.get("products");
        if (productsObj instanceof List) allProducts.addAll((List<Map<String, Object>>) productsObj);

        Object catalogObj = catalogData.get("catalog");
        if (catalogObj instanceof List) {
            extractProductsFromCatalogForSearch((List<Map<String, Object>>) catalogObj, allProducts, query);
        }

        for (Map<String, Object> product : allProducts) {
            String productName = Objects.toString(product.get("name"), "");
            if (productName.toLowerCase().contains(query)) {
                found.add(product);
            }
        }

        return found;
    }

    // 🔹 Public метод для рекурсивного пошуку товарів у catalog
    public void extractProductsFromCatalogForSearch(
            List<Map<String, Object>> groups,
            List<Map<String, Object>> foundProducts,
            String query) {
        for (Map<String, Object> group : groups) {
            if (group.containsKey("products")) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) group.get("products");
                for (Map<String, Object> p : products) {
                    String productName = Objects.toString(p.get("name"), "");
                    if (productName.toLowerCase().contains(query)) {
                        Map<String, Object> copy = new HashMap<>(p);
                        copy.put("category", group.getOrDefault("name", ""));
                        foundProducts.add(copy);
                    }
                }
            }

            if (group.containsKey("subgroups")) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) group.get("subgroups");
                extractProductsFromCatalogForSearch(subgroups, foundProducts, query);
            }
        }
    }

    // 🔹 Повертає список категорій
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");
        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                categories.add(Objects.toString(cat.get("name"), ""));
            }
        }
        return categories;
    }

    // 🔹 Повертає підкатегорії категорії
    public List<String> getSubcategories(String categoryName) {
        List<String> subcategories = new ArrayList<>();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");
        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                if (Objects.equals(cat.get("name"), categoryName)) {
                    List<Map<String, Object>> subgroups = (List<Map<String, Object>>) cat.get("subgroups");
                    if (subgroups != null) {
                        for (Map<String, Object> subgroup : subgroups) {
                            subcategories.add(Objects.toString(subgroup.get("name"), ""));
                        }
                    }
                }
            }
        }
        return subcategories;
    }

    // 🔹 Повертає товари з підкатегорії
    public List<Map<String, Object>> getProducts(String categoryName, String subcategoryName) {
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) catalogData.get("catalog");
        if (catalog != null) {
            for (Map<String, Object> cat : catalog) {
                if (Objects.equals(cat.get("name"), categoryName)) {
                    List<Map<String, Object>> subgroups = (List<Map<String, Object>>) cat.get("subgroups");
                    if (subgroups != null) {
                        for (Map<String, Object> subgroup : subgroups) {
                            if (Objects.equals(subgroup.get("name"), subcategoryName)) {
                                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                                if (products != null) {
                                    for (Map<String, Object> p : products) {
                                        p.putIfAbsent("photo", "");
                                    }
                                    return products;
                                } else return new ArrayList<>();
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    // 🔹 Повертає весь catalog (плоский варіант)
    public List<Map<String, Object>> getCatalog() {
        if (catalogData.containsKey("catalog")) {
            return (List<Map<String, Object>>) catalogData.get("catalog");
        }
        if (catalogData.containsKey("products")) {
            return (List<Map<String, Object>>) catalogData.get("products");
        }
        return new ArrayList<>();
    }
}
