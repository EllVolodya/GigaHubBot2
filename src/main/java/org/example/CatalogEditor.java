package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogEditor {

    private static final String CATALOG_PATH = "/app/catalog.yml"; // Railway

    // --- Завантажує YAML як Map ---
    public static Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(CATALOG_PATH);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map) return (Map<String, Object>) loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new LinkedHashMap<>();
    }

    // --- Зберігає Map у YAML ---
    public static void saveCatalog(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        Yaml yaml = new Yaml(options);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CATALOG_PATH), StandardCharsets.UTF_8)) {
            yaml.dump(data, writer);
            System.out.println("[YAML] Каталог збережено: " + CATALOG_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Перевірка чи існує категорія ---
    public static boolean categoryExists(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        return categories.stream().anyMatch(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
    }

    // --- Додати категорію ---
    public static boolean addCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.computeIfAbsent("catalog", k -> new ArrayList<>());

        boolean exists = categories.stream().anyMatch(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
        if (exists) return false;

        Map<String, Object> newCategory = new LinkedHashMap<>();
        newCategory.put("name", categoryName);
        newCategory.put("subgroups", new ArrayList<>());
        categories.add(newCategory);

        saveCatalog(data);
        return true;
    }

    // --- Додати підкатегорію ---
    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        for (Map<String, Object> category : categories) {
            if (Objects.toString(category.get("name"), "").equalsIgnoreCase(categoryName)) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.computeIfAbsent("subgroups", k -> new ArrayList<>());
                boolean exists = subgroups.stream().anyMatch(s -> Objects.toString(s.get("name"), "").equalsIgnoreCase(subcategoryName));
                if (!exists) {
                    Map<String, Object> newSub = new LinkedHashMap<>();
                    newSub.put("name", subcategoryName);
                    newSub.put("products", new ArrayList<>());
                    subgroups.add(newSub);
                    saveCatalog(data);
                    return true;
                }
            }
        }
        return false;
    }

    // --- Видалити категорію ---
    public static boolean deleteCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        boolean removed = categories.removeIf(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));

        if (removed) saveCatalog(data);
        return removed;
    }

    // --- Оновити поле товару ---
    public static void updateField(String productName, String field, String newValue) {
        Map<String, Object> data = loadCatalog();
        boolean updated = false;

        if (!data.containsKey("catalog")) return;
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");

        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            if (subgroups == null) continue;

            for (Map<String, Object> subgroup : subgroups) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                if (products == null) continue;

                for (Map<String, Object> product : products) {
                    String name = Objects.toString(product.get("name"), "").trim();
                    if (name.equalsIgnoreCase(productName.trim())) {
                        product.put(field, newValue);
                        updated = true;
                        break;
                    }
                }
                if (updated) break;
            }
            if (updated) break;
        }

        if (updated) saveCatalog(data);
    }

    // --- Оновити виробника товару ---
    public static boolean updateProductManufacturer(String productName, String manufacturerValue) {
        updateField(productName, "manufacturer", manufacturerValue);
        return true;
    }

    // --- Додати продукт у підкатегорію ---
    public static boolean addProductToSubcategory(String productName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
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
                    List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.computeIfAbsent("products", k -> new ArrayList<>());
                    boolean exists = products.stream()
                            .anyMatch(p -> Objects.toString(p.get("name"), "").equalsIgnoreCase(productName));
                    if (!exists) {
                        products.add(productCopy);
                        saveCatalog(data);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
