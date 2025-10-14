package org.example;

import java.util.*;

public class CatalogEditor {

    // --- Додати категорію
    public static boolean addCategory(String categoryName) {
        Map<String, Object> data = CatalogManager.loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) {
            catalog = new ArrayList<>();
            data.put("catalog", catalog);
        }

        boolean exists = catalog.stream()
                .anyMatch(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
        if (exists) return false;

        Map<String, Object> newCategory = new LinkedHashMap<>();
        newCategory.put("name", categoryName);
        newCategory.put("subgroups", new ArrayList<>());
        catalog.add(newCategory);

        CatalogManager.saveCatalog(data);
        return true;
    }

    // --- Перевірити існування категорії
    public static boolean categoryExists(String categoryName) {
        Map<String, Object> data = CatalogManager.loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) return false;

        return catalog.stream()
                .anyMatch(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
    }

    // --- Додати підкатегорію
    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = CatalogManager.loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) return false;

        for (Map<String, Object> category : catalog) {
            if (Objects.toString(category.get("name"), "").equalsIgnoreCase(categoryName)) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
                if (subgroups == null) {
                    subgroups = new ArrayList<>();
                    category.put("subgroups", subgroups);
                }

                boolean exists = subgroups.stream()
                        .anyMatch(s -> Objects.toString(s.get("name"), "").equalsIgnoreCase(subcategoryName));
                if (exists) return false;

                Map<String, Object> newSub = new LinkedHashMap<>();
                newSub.put("name", subcategoryName);
                newSub.put("products", new ArrayList<>());
                subgroups.add(newSub);

                CatalogManager.saveCatalog(data);
                return true;
            }
        }
        return false;
    }

    // --- Видалити категорію
    public static boolean deleteCategory(String categoryName) {
        Map<String, Object> data = CatalogManager.loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) return false;

        boolean removed = catalog.removeIf(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
        if (removed) CatalogManager.saveCatalog(data);
        return removed;
    }

    // --- Оновити будь-яке поле продукту
    public static boolean updateField(String productName, String field, Object value) {
        Map<String, Object> data = CatalogManager.loadCatalog();

        List<Map<String, Object>> rootProducts = (List<Map<String, Object>>) data.get("products");
        if (rootProducts != null) {
            for (Map<String, Object> p : rootProducts) {
                if (Objects.toString(p.get("name"), "").equalsIgnoreCase(productName)) {
                    p.put(field, value);
                    CatalogManager.saveCatalog(data);
                    return true;
                }
            }
        }

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog != null) {
            return updateFieldInCatalog(catalog, productName, field, value, data);
        }

        return false;
    }

    private static boolean updateFieldInCatalog(List<Map<String, Object>> groups, String productName, String field, Object value, Map<String, Object> fullData) {
        for (Map<String, Object> group : groups) {
            List<Map<String, Object>> products = (List<Map<String, Object>>) group.get("products");
            if (products != null) {
                for (Map<String, Object> p : products) {
                    if (Objects.toString(p.get("name"), "").equalsIgnoreCase(productName)) {
                        p.put(field, value);
                        CatalogManager.saveCatalog(fullData);
                        return true;
                    }
                }
            }

            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) group.get("subgroups");
            if (subgroups != null) {
                boolean updated = updateFieldInCatalog(subgroups, productName, field, value, fullData);
                if (updated) return true;
            }
        }
        return false;
    }

    // --- Оновити виробника
    public static boolean updateProductManufacturer(String productName, String manufacturer) {
        return updateField(productName, "manufacturer", manufacturer);
    }

    // --- Додати продукт у підкатегорію
    public static boolean addProductToSubcategory(String productName, String subcategoryName) {
        Map<String, Object> data = CatalogManager.loadCatalog();
        if (!data.containsKey("catalog")) return false;

        Map<String, Object> foundProduct = null;

        if (data.containsKey("products")) {
            List<Map<String, Object>> rootProducts = (List<Map<String, Object>>) data.get("products");
            for (Map<String, Object> p : rootProducts) {
                if (Objects.toString(p.get("name"), "").equalsIgnoreCase(productName)) {
                    foundProduct = p;
                    break;
                }
            }
        }

        if (foundProduct == null) return false;

        Map<String, Object> productCopy = new LinkedHashMap<>(foundProduct);
        productCopy.putIfAbsent("description", "");
        productCopy.putIfAbsent("photo", "");
        productCopy.putIfAbsent("unit", "шт");

        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            if (subgroups == null) continue;

            for (Map<String, Object> subgroup : subgroups) {
                if (Objects.toString(subgroup.get("name"), "").equalsIgnoreCase(subcategoryName)) {
                    List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                    if (products == null) {
                        products = new ArrayList<>();
                        subgroup.put("products", products);
                    }

                    boolean exists = products.stream()
                            .anyMatch(p -> Objects.toString(p.get("name"), "").equalsIgnoreCase(productName));
                    if (!exists) {
                        products.add(productCopy);
                        CatalogManager.saveCatalog(data);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
