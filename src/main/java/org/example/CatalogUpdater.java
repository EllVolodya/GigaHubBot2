package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CatalogUpdater {

    private static final String CATALOG_RESOURCE = "catalog.yml";

    // ---------------------- Завантаження всіх продуктів ----------------------
    public static List<Map<String, Object>> loadProducts() throws IOException {
        InputStream in = CatalogUpdater.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE);
        if (in == null) throw new IOException("catalog.yml не знайдено у ресурсах");
        Yaml yaml = new Yaml();
        List<Map<String, Object>> products = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        if (products == null) products = new ArrayList<>();
        in.close();
        return products;
    }

    // ---------------------- Пошук продуктів за ключовими словами ----------------------
    public static List<Map<String, Object>> searchProductsByKeywords(String userInput) throws IOException {
        List<Map<String, Object>> products = loadProducts();
        List<Map<String, Object>> result = new ArrayList<>();
        String[] keywords = userInput.toLowerCase().split("\\s+");

        for (Map<String, Object> product : products) {
            String name = ((String) product.get("name")).toLowerCase();
            boolean matches = true;
            for (String kw : keywords) {
                if (!name.contains(kw)) {
                    matches = false;
                    break;
                }
            }
            if (matches) result.add(product);
        }

        return result;
    }

    // ---------------------- Оновлення продукту ----------------------
    public static void updateProduct(String productName, String field, String newValue) throws IOException {
        // Для оновлення читаємо файл із filesystem, бо клас-пас ресурс read-only у JAR
        File file = new File("src/main/resources/catalog.yml");
        if (!file.exists()) throw new IOException("Файл catalog.yml не знайдено на диску");

        Yaml yaml = new Yaml();
        List<Map<String, Object>> products;
        try (InputStream in = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            products = yaml.load(reader);
            if (products == null) products = new ArrayList<>();
        }

        boolean found = false;
        for (Map<String, Object> product : products) {
            if (productName.equalsIgnoreCase((String) product.get("name"))) {
                product.put(field, newValue);
                found = true;
                break;
            }
        }

        if (!found) throw new IOException("Товар '" + productName + "' не знайдено.");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yamlWriter = new Yaml(options);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            yamlWriter.dump(products, writer);
        }
    }

    public static List<Map<String, Object>> searchProductsSimple(String userInput) {
        Yaml yaml = new Yaml();
        List<Map<String, Object>> result = new ArrayList<>();

        try (InputStream in = CatalogUpdater.class.getClassLoader().getResourceAsStream("catalog.yml")) {
            if (in == null) {
                System.out.println("❌ catalog.yml не знайдено у resources!");
                return result;
            }

            Map<String, Object> data = yaml.load(in);
            if (data == null || !data.containsKey("products")) return result;

            List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("products");
            if (products == null) return result;

            String[] keywords = userInput.toLowerCase().split("\\s+");

            for (Map<String, Object> product : products) {
                String name = ((String) product.get("name")).toLowerCase();
                boolean matches = true;

                for (String kw : keywords) {
                    if (!name.contains(kw)) {
                        matches = false;
                        break;
                    }
                }

                if (matches) result.add(product);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}