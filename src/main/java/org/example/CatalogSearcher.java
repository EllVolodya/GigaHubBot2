package org.example;

import java.util.*;

public class CatalogSearcher {

    private final Map<String, Object> catalogData;

    public CatalogSearcher() {
        this.catalogData = CatalogManager.loadCatalog();
        System.out.println("✅ Catalog loaded: keys = " + catalogData.keySet());
    }

    // ---------------- Плоский список продуктів ----------------
    public List<Map<String, Object>> getFlatProducts() {
        List<Map<String, Object>> products = (List<Map<String, Object>>) catalogData.get("products");
        return products != null ? products : new ArrayList<>();
    }

    public List<Map<String, Object>> searchByKeywordsAdmin(String keywords) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (keywords == null || keywords.isEmpty()) return results;

        String[] parts = keywords.toLowerCase().split("\\s+");
        for (Map<String, Object> product : getFlatProducts()) {
            String name = Objects.toString(product.get("name"), "").toLowerCase();
            boolean allMatch = Arrays.stream(parts).allMatch(name::contains);
            if (allMatch) results.add(product);
        }
        return results;
    }

    // ---------------- Пошук продуктів у catalog ----------------
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

    // ---------------- Рекурсивний пошук ----------------
    public void extractProductsFromCatalogForSearch(List<Map<String, Object>> groups,
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

    // ---------------- Категорії ----------------
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
                                if (products != null) return products;
                                else return new ArrayList<>();
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>();
    }

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
