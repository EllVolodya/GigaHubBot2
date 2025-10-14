package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogManager {
    private static final String FILE_PATH = "src/main/resources/catalog.yml";

    // Завантажити весь каталог
    public static Map<String, Object> loadCatalog() throws IOException {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }
        try (InputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml();
            return yaml.load(reader);
        }
    }

    // Зберегти весь каталог
    public static void saveCatalog(Map<String, Object> catalog) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        Yaml yaml = new Yaml(options);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE_PATH), StandardCharsets.UTF_8)) {
            yaml.dump(catalog, writer);
        }
    }

    // Оновлення поля товару (Назва, Ціна, Опис)
    public static void updateProduct(String productName, String field, String newValue) throws IOException {
        Map<String, Object> catalogData = loadCatalog();
        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalogData.get("catalog");

        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            for (Map<String, Object> subgroup : subgroups) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                for (Map<String, Object> product : products) {
                    if (productName.equals(product.get("name"))) {
                        product.put(field, newValue);
                        saveCatalog(catalogData);
                        return;
                    }
                }
            }
        }
        throw new IOException("Товар для оновлення не знайдено.");
    }

    // Додати існуючий товар до нової категорії
    public static void addProductToCategory(String productName, String newCategoryName) throws IOException {
        Map<String, Object> catalogData = loadCatalog();
        Map<String, Object> productToAdd = findProduct(productName, catalogData);

        if (productToAdd == null) throw new IOException("Товар не знайдено.");

        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalogData.get("catalog");
        Map<String, Object> targetSubgroup = findOrCreateSubgroup(categories, newCategoryName);

        if (targetSubgroup != null) {
            List<Map<String, Object>> targetProducts = (List<Map<String, Object>>) targetSubgroup.get("products");
            targetProducts.add(new LinkedHashMap<>(productToAdd));
            saveCatalog(catalogData);
        } else {
            throw new IOException("Цільову категорію не знайдено.");
        }
    }

    // Додати нову категорію
    public static void addCategory(String name) throws IOException {
        Map<String, Object> catalogData = loadCatalog();
        if (!catalogData.containsKey("catalog")) {
            catalogData.put("catalog", new ArrayList<Map<String, Object>>());
        }
        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalogData.get("catalog");

        Map<String, Object> newCat = new LinkedHashMap<>();
        newCat.put("name", name);
        newCat.put("subgroups", new ArrayList<>());

        categories.add(newCat);
        saveCatalog(catalogData);
    }

    // Пошук товару в каталозі
    public static Map<String, Object> findProduct(String productName, Map<String, Object> catalogData) {
        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalogData.get("catalog");
        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            for (Map<String, Object> subgroup : subgroups) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                for (Map<String, Object> product : products) {
                    if (product.get("name") != null && productName.equals(product.get("name"))) {
                        return product;
                    }
                }
            }
        }
        return null;
    }

    // Отримати список усіх товарів (плоский список)
    public static List<Map<String, Object>> getFlatProductList(Map<String, Object> catalogData) {
        List<Map<String, Object>> flatList = new ArrayList<>();
        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalogData.get("catalog");
        if (categories != null) {
            for (Map<String, Object> cat : categories) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) cat.get("subgroups");
                if (subgroups != null) {
                    for (Map<String, Object> subgroup : subgroups) {
                        List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                        if (products != null) flatList.addAll(products);
                    }
                }
            }
        }
        return flatList;
    }

    // Знайти або створити підгрупу для додавання товару
    private static Map<String, Object> findOrCreateSubgroup(List<Map<String, Object>> categories, String categoryName) {
        for (Map<String, Object> category : categories) {
            if (categoryName.equals(category.get("name"))) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
                if (!subgroups.isEmpty()) {
                    return subgroups.get(0);
                } else {
                    Map<String, Object> newSubgroup = new LinkedHashMap<>();
                    newSubgroup.put("name", "Загальна");
                    newSubgroup.put("products", new ArrayList<>());
                    subgroups.add(newSubgroup);
                    return newSubgroup;
                }
            }
        }
        return null;
    }
}