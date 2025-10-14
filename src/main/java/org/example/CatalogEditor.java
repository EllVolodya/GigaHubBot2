package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogEditor {

    private static final String CATALOG_PATH = "/app/catalog.yml"; // Зовнішній файл на Railway

    // --- Завантаження YAML ---
    public static Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(CATALOG_PATH);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (FileNotFoundException e) {
            System.out.println("[YAML] Файл catalog.yml не знайдений: " + CATALOG_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new LinkedHashMap<>();
    }

    // --- Збереження YAML ---
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

    // --- Додати категорію ---
    public static boolean addCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
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

        saveCatalog(data);
        return true;
    }

    // --- Перевірити існування категорії ---
    public static boolean categoryExists(String categoryName) {
        Map<String, Object> data = loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) return false;

        return catalog.stream()
                .anyMatch(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
    }

    // --- Додати підкатегорію ---
    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
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

                saveCatalog(data);
                return true;
            }
        }
        return false;
    }

    // --- Видалити категорію ---
    public static boolean deleteCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) return false;

        boolean removed = catalog.removeIf(c -> Objects.toString(c.get("name"), "").equalsIgnoreCase(categoryName));
        if (removed) saveCatalog(data);
        return removed;
    }

    // --- Оновити будь-яке поле продукту ---
    public static boolean updateField(String productName, String field, Object value) {
        Map<String, Object> data = loadCatalog();

        // 🔹 Плоский список products
        List<Map<String, Object>> rootProducts = (List<Map<String, Object>>) data.get("products");
        if (rootProducts != null) {
            for (Map<String, Object> p : rootProducts) {
                if (Objects.toString(p.get("name"), "").equalsIgnoreCase(productName)) {
                    p.put(field, value);
                    saveCatalog(data);
                    return true;
                }
            }
        }

        // 🔹 Рекурсивно через catalog
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog != null) {
            return updateFieldInCatalog(catalog, productName, field, value);
        }

        return false;
    }

    private static boolean updateFieldInCatalog(List<Map<String, Object>> groups, String productName, String field, Object value) {
        for (Map<String, Object> group : groups) {
            List<Map<String, Object>> products = (List<Map<String, Object>>) group.get("products");
            if (products != null) {
                for (Map<String, Object> p : products) {
                    if (Objects.toString(p.get("name"), "").equalsIgnoreCase(productName)) {
                        p.put(field, value);
                        saveCatalog(loadCatalog());
                        return true;
                    }
                }
            }

            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) group.get("subgroups");
            if (subgroups != null) {
                boolean updated = updateFieldInCatalog(subgroups, productName, field, value);
                if (updated) return true;
            }
        }
        return false;
    }

    // --- Оновити виробника продукту ---
    public static boolean updateProductManufacturer(String productName, String manufacturer) {
        return updateField(productName, "manufacturer", manufacturer);
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
                    List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                    if (products == null) {
                        products = new ArrayList<>();
                        subgroup.put("products", products);
                    }

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
