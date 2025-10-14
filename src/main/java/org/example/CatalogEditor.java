package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogEditor {

    // --- Зовнішній файл, куди будемо зберігати оновлення ---
    private static final String EXTERNAL_CATALOG_PATH = "catalog.yml";

    // --- Завантажує YAML: спочатку перевіряємо зовнішній файл, якщо немає, беремо ресурс ---
    public static Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        InputStream in = null;

        // 1) Спроба зчитати зовнішній файл (створений після оновлень)
        File externalFile = new File(EXTERNAL_CATALOG_PATH);
        if (externalFile.exists()) {
            try {
                in = new FileInputStream(externalFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        // 2) Якщо зовнішнього файлу нема, беремо ресурс із JAR
        if (in == null) {
            in = CatalogEditor.class.getClassLoader().getResourceAsStream("catalog.yml");
            if (in == null) {
                System.err.println("[YAML] catalog.yml не знайдено!");
                return new LinkedHashMap<>();
            }
        }

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new LinkedHashMap<>();
    }

    // --- Збереження каталогу у зовнішній файл ---
    public static void saveCatalog(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        Yaml yaml = new Yaml(options);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(EXTERNAL_CATALOG_PATH), StandardCharsets.UTF_8)) {
            yaml.dump(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Повертає усі товари у плоскому списку (root products + категорії) ---
    public static List<Map<String, Object>> loadCatalogListFlat() {
        List<Map<String, Object>> products = new ArrayList<>();
        Map<String, Object> data = loadCatalog();
        if (data == null || data.isEmpty()) return products;

        if (data.containsKey("products")) {
            List<Map<String, Object>> rootProducts = (List<Map<String, Object>>) data.get("products");
            if (rootProducts != null) products.addAll(rootProducts);
        }

        if (!data.containsKey("catalog")) return products;
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        if (categories == null) return products;

        for (Map<String, Object> category : categories) {
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

    // --- Методи для редагування товару ---
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
                    String yamlName = Objects.toString(product.get("name"), "").trim();
                    if (yamlName.equalsIgnoreCase(productName.trim())) {
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

    public static void renameProduct(String oldName, String newName) {
        updateField(oldName, "name", newName);
    }

    public static void updatePrice(String productName, String newPrice) {
        updateField(productName, "price", newPrice);
    }

    public static void updateDescription(String productName, String newDescription) {
        updateField(productName, "description", newDescription);
    }

    // --- Додавання категорій/підкатегорій ---
    public static void addCategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        if (catalog == null) catalog = new ArrayList<>();

        Map<String, Object> newCategory = new LinkedHashMap<>();
        newCategory.put("name", categoryName);

        List<Map<String, Object>> subgroups = new ArrayList<>();
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("name", subcategoryName != null && !subcategoryName.isEmpty() ? subcategoryName : "Основна");
        sub.put("products", new ArrayList<>());
        subgroups.add(sub);

        newCategory.put("subgroups", subgroups);
        catalog.add(newCategory);
        data.put("catalog", catalog);

        saveCatalog(data);
    }

    public static boolean categoryExists(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        return catalog.stream().anyMatch(cat -> categoryName.equalsIgnoreCase(Objects.toString(cat.get("name"), "")));
    }

    // --- Додавання продукту в підкатегорію ---
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

    public static boolean addSubcategory(String categoryName, String subcategoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        for (Map<String, Object> category : catalog) {
            String currentName = Objects.toString(category.get("name"), "");
            if (currentName.equalsIgnoreCase(categoryName)) {
                List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
                if (subgroups == null) {
                    subgroups = new ArrayList<>();
                    category.put("subgroups", subgroups);
                }

                boolean exists = subgroups.stream()
                        .anyMatch(sg -> subcategoryName.equalsIgnoreCase(Objects.toString(sg.get("name"), "")));
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

    public static boolean deleteCategory(String categoryName) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("catalog")) return false;

        List<Map<String, Object>> catalog = (List<Map<String, Object>>) data.get("catalog");
        Iterator<Map<String, Object>> iterator = catalog.iterator();
        boolean removed = false;

        while (iterator.hasNext()) {
            Map<String, Object> category = iterator.next();
            if (categoryName.equalsIgnoreCase(Objects.toString(category.get("name"), ""))) {
                iterator.remove();
                removed = true;
                break;
            }
        }

        if (removed) saveCatalog(data);
        return removed;
    }

    @SuppressWarnings("unchecked")
    public static boolean updateProductManufacturer(String productName, String manufacturerValue) {
        Map<String, Object> data = loadCatalog();
        if (!data.containsKey("products")) {
            System.err.println("ERROR: catalog.yml не має секції 'products'");
            return false;
        }

        List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("products");
        boolean updated = false;

        for (Map<String, Object> product : products) {
            String name = Objects.toString(product.get("name"), "");
            if (name.equalsIgnoreCase(productName)) {
                if (manufacturerValue.equalsIgnoreCase("❌") || manufacturerValue.isEmpty()) {
                    product.remove("manufacturer");
                } else {
                    product.put("manufacturer", manufacturerValue.trim());
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
}
