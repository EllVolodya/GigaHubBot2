package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;

public class CatalogUpdater {
    private static final String CATALOG_PATH = "src/main/resources/catalog.yml";

    public static List<Map<String, Object>> loadProducts() {
        Yaml yaml = new Yaml();

        try {
            Object var4;
            try (
                    InputStream in = new FileInputStream("src/main/resources/catalog.yml");
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            ) {
                List<Map<String, Object>> products = (List)yaml.load(reader);
                if (products == null) {
                    products = new ArrayList();
                }

                var4 = products;
            }

            return (List<Map<String, Object>>)var4;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList();
        }
    }

    public static void updateProduct(String productName, String field, String newValue) throws IOException {
        List<Map<String, Object>> products = loadProducts();
        boolean found = false;

        for(Map<String, Object> product : products) {
            if (productName.equalsIgnoreCase((String)product.get("name"))) {
                product.put(field, newValue);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IOException("Товар '" + productName + "' не знайдено.");
        } else {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream("src/main/resources/catalog.yml"), StandardCharsets.UTF_8)) {
                yaml.dump(products, writer);
            }

        }
    }

    public static void updateCatalog(String chatId, StoreBot bot) {
    }

    public static Map<String, Object> loadCatalog() throws IOException {
        Yaml yaml = new Yaml();

        Map var3;
        try (
                InputStream in = new FileInputStream("src/main/resources/catalog.yml");
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        ) {
            var3 = (Map)yaml.load(reader);
        }

        return var3;
    }

    public static List<Map<String, Object>> searchProductsByKeywords(String userInput) throws IOException {
        Map<String, Object> data = loadCatalog();
        List<Map<String, Object>> result = new ArrayList<>();

        if (!data.containsKey("catalog")) {
            return result;
        }

        // Кастимо каталог до списку категорій
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("catalog");
        String[] keywords = userInput.toLowerCase().split("\\s+");

        for (Map<String, Object> category : categories) {
            // Кастимо підкатегорії
            List<Map<String, Object>> subgroups = (List<Map<String, Object>>) category.get("subgroups");
            for (Map<String, Object> subgroup : subgroups) {
                // Кастимо продукти
                List<Map<String, Object>> products = (List<Map<String, Object>>) subgroup.get("products");
                for (Map<String, Object> product : products) {
                    String name = ((String) product.get("name")).toLowerCase();
                    boolean matches = true;

                    for (String kw : keywords) {
                        if (!name.contains(kw)) {
                            matches = false;
                            break;
                        }
                    }

                    if (matches) {
                        result.add(product);
                    }
                }
            }
        }

        return result;
    }

}