package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogEditor {

    private static final String CATALOG_PATH = "src/main/resources/catalog.yml";

    // --- Завантажує весь YAML як Map ---
    public static Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(CATALOG_PATH);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new LinkedHashMap<>();
    }

    // --- Повертає усі товари у плоскому списку (включно з root "products:") ---
    public static List<Map<String, Object>> loadCatalogListFlat() {
        List<Map<String, Object>> products = new ArrayList<>();
        Map<String, Object> data = loadCatalog();
        if (data == null || data.isEmpty()) return products;

        // 1) root products:
        if (data.containsKey("products")) {
            List<Map<String, Object>> rootProducts = (List<Map<String, Object>>) data.get("products");
            if (rootProducts != null) products.addAll(rootProducts);
        }

        // 2) products in catalog -> categories and subgroups
        if (!data.containsKey("catalog")) return products;
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        if (categories == null) return products;

        for (Map<String, Object> category : categories) {
            // products defined directly under category
            List<Map<String, Object>> directProducts = (List<Map<String, Object>>) category.get("products");
            if (directProducts != null) products.addAll(directProducts);

            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            if (subgroups != null) {
                for (Map<String, Object> subgroup : subgroups) {
                    List<Map<String, Object>> subgroupProducts = (List<Map<String, Object>>) subgroup.get("products");
                    if (subgroupProducts != null) products.addAll(subgroupProducts);
                }
            }
        }
        return products;
    }

    // --- Перейменувати товар ---
    public static void renameProduct(String oldName, String newName) {
        updateField(oldName, "name", newName);
    }

    // --- Оновити ціну ---
    public static void updatePrice(String productName, String newPrice) {
        updateField(productName, "price", newPrice);
    }

    // --- Оновити опис ---
    public static void updateDescription(String productName, String newDescription) {
        updateField(productName, "description", newDescription);
    }

    // --- Загальний метод для оновлення поля товару ---
    public static void updateField(String productName, String field, String newValue) {
        Map<String, Object> data = loadCatalog();
        boolean updated = false;

        if (!data.containsKey("catalog")) {
            System.out.println("[YAML] Каталог порожній або не знайдено ключ 'catalog'");
            return;
        }

        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");

        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            if (subgroups == null) continue;

            for (Map<String, Object> subgroup : subgroups) {
                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                if (products == null) continue;

                for (Map<String, Object> product : products) {
                    String yamlName = ((String) product.get("name")).trim().replaceAll("[\"']", "");
                    String searchName = productName.trim().replaceAll("[\"']", "");

                    System.out.println("[DEBUG] Порівнюємо: '" + yamlName + "' з '" + searchName + "'");

                    if (yamlName.equalsIgnoreCase(searchName)) {
                        product.put(field, newValue);
                        updated = true;
                        System.out.println("[YAML] Поле '" + field + "' оновлено для товару: " + product.get("name"));
                        break;
                    }
                }
                if (updated) break;
            }
            if (updated) break;
        }

        if (updated) {
            saveCatalog(data);
            System.out.println("[YAML] Каталог успішно збережено після оновлення.");
        } else {
            System.out.println("[YAML] Товар '" + productName + "' не знайдено в каталозі.");
        }
    }
    // --- Додати категорію (створює підкатегорію і порожній products) ---

    public static void addCategory(String categoryName, String subcategoryName) {
        try {
            Map<String, Object> data = loadCatalog();

            List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
            if (catalog == null) catalog = new ArrayList<>();

            Map<String, Object> newCategory = new LinkedHashMap<>();
            newCategory.put("name", categoryName);

            List<Map<String, Object>> subgroups = new ArrayList<>();
            Map<String, Object> sub = new LinkedHashMap<>();

            if (subcategoryName != null && !subcategoryName.isEmpty()) {
                sub.put("name", subcategoryName);
            } else {
                sub.put("name", "Основна"); // якщо підкатегорію не ввели
            }

            // Створюємо порожній products завжди
            sub.put("products", new ArrayList<>());
            subgroups.add(sub);

            newCategory.put("subgroups", subgroups);
            catalog.add(newCategory);
            data.put("catalog", catalog);

            saveCatalog(data);

            System.out.println("INFO: Категорія '" + categoryName + "' додана" + (subcategoryName != null && !subcategoryName.isEmpty() ? " з підкатегорією '" + subcategoryName + "'" : ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Видалення категорії ---
    public static boolean deleteCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        Iterator<Map<String, Object>> iterator = categories.iterator();
        boolean removed = false;

        while (iterator.hasNext()) {
            Map<String, Object> category = iterator.next();
            if (categoryName.equals(category.get("name"))) {
                iterator.remove();
                removed = true;
                break;
            }
        }

        if (removed) saveCatalog(data);
        return removed;
    }

    // --- ЄДИНИЙ метод normalize в класі (не дублюй його!) ---
    private static String normalize(String s) {
        if (s == null) return "";
        // видаляє зайві пробіли (включно з не-розривними), приводить до нижнього регістру
        return s.replaceAll("[\\u00A0\\s]+", " ").trim().toLowerCase();
    }

    // --- Додавання товару у підкатегорію: спочатку шукаємо в root products:, потім додаємо ---
    public static boolean addProductToSubcategory(String productName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        Map<String, Object> foundProduct = null;

        // 1) Шукаємо в root "products:"
        if (data.containsKey("products")) {
            List<Map<String, Object>> productsSection = (List<Map<String, Object>>) data.get("products");
            if (productsSection != null) {
                for (Map<String, Object> p : productsSection) {
                    String pName = (String) p.get("name");
                    if (normalize(pName).equals(normalize(productName))) {
                        foundProduct = p;
                        break;
                    }
                }
            }
        }

        // 2) Якщо не знайдено у root products, шукаємо у плоскому списку (категорії/підкатегорії)
        if (foundProduct == null) {
            List<Map<String, Object>> allProducts = loadCatalogListFlat();
            for (Map<String, Object> p : allProducts) {
                String pName = (String) p.get("name");
                if (normalize(pName).equals(normalize(productName))) {
                    foundProduct = p;
                    break;
                }
            }
        }

        if (foundProduct == null) {
            System.err.println("ERROR: товар '" + productName + "' не знайдено у products або catalog — не додаємо.");
            return false; // ми НЕ створюємо новий товар автоматично
        }

        // Підготовка копії (name+price) + пустий description + unit + manufacturer
        Map<String, Object> productCopy = new LinkedHashMap<>();
        productCopy.put("name", foundProduct.get("name"));
        productCopy.put("price", foundProduct.getOrDefault("price", "0"));
        productCopy.put("description", "");
        productCopy.put("photo", "");
        productCopy.put("unit", foundProduct.getOrDefault("unit", "шт"));

        // ✅ Додаємо виробника, якщо він існує
        if (foundProduct.containsKey("manufacturer")) {
            productCopy.put("manufacturer", foundProduct.get("manufacturer"));
        }

        // 3) Знаходимо підкатегорію і додаємо (без дублів)
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        boolean added = false;

        for (Map<String, Object> category : categories) {
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            if (subgroups == null) continue;

            for (Map<String, Object> subgroup : subgroups) {
                String subgroupNameActual = (String) subgroup.get("name");
                if (normalize(subgroupNameActual).equals(normalize(subcategoryName))) {
                    List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                    if (products == null) {
                        products = new ArrayList<>();
                        subgroup.put("products", products);
                    }

                    boolean exists = products.stream()
                            .anyMatch(p -> normalize((String) p.get("name")).equals(normalize((String) productCopy.get("name"))));
                    if (!exists) {
                        products.add(productCopy);
                        added = true;
                    }
                    break;
                }
            }
            if (added) break;
        }

        if (!added) {
            System.err.println("ERROR: Не знайдено підкатегорію '" + subcategoryName + "' у YAML або товар уже існує.");
            return false;
        }

        saveCatalog(data);
        System.out.println("INFO: Товар '" + productName + "' додано у підкатегорію '" + subcategoryName + "'");
        return true;
    }

    public static boolean categoryExists(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        return catalog.stream().anyMatch(cat ->
                categoryName.equalsIgnoreCase((String) cat.get("name")));
    }

    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");

        for (Map<String, Object> category : catalog) {
            String currentName = (String) category.get("name");
            if (currentName.equalsIgnoreCase(categoryName)) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
                if (subgroups == null) {
                    subgroups = new ArrayList<>();
                    category.put("subgroups", subgroups);
                }

                // Перевіряємо, чи підкатегорія вже існує
                boolean exists = subgroups.stream().anyMatch(sg ->
                        subcategoryName.equalsIgnoreCase((String) sg.get("name")));

                if (exists) return false;

                Map<String, Object> newSub = new LinkedHashMap<>();
                newSub.put("name", subcategoryName);
                newSub.put("products", new ArrayList<>()); // пустий список продуктів
                subgroups.add(newSub);

                saveCatalog(data);
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public static boolean updateProductManufacturer(String productName, String manufacturerValue) {
        Map<String, Object> data = loadCatalog();
        if (data == null || !data.containsKey("products")) {
            System.err.println("ERROR: catalog.yml не має секції 'products'");
            return false;
        }

        List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("products");
        boolean updated = false;

        for (Map<String, Object> product : products) {
            String name = Objects.toString(product.get("name"), "");
            if (normalize(name).equals(normalize(productName))) {
                if (manufacturerValue.equalsIgnoreCase("❌") || manufacturerValue.isEmpty()) {
                    product.remove("manufacturer"); // ❌ видаляємо виробника
                    System.out.println("INFO: Виробника видалено для товару '" + productName + "'");
                } else {
                    product.put("manufacturer", manufacturerValue.trim()); // ✅ оновлюємо/додаємо
                    System.out.println("INFO: Виробник оновлений для '" + productName + "': " + manufacturerValue);
                }
                updated = true;
                break;
            }
        }

        if (!updated) {
            System.err.println("ERROR: Товар '" + productName + "' не знайдено у catalog.yml");
            return false;
        }

        saveCatalog(data);
        return true;
    }

    // --- Зберігає Map назад у YAML (public щоб інші класи могли викликати) ---
    public static void saveCatalog(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        Yaml yaml = new Yaml(options);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CATALOG_PATH), StandardCharsets.UTF_8)) {
            yaml.dump(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
