package org.example;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class CatalogLoader {
    private List<Map<String, Object>> products;

    public CatalogLoader() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("catalogs.yml")) {
            if (in != null) {
                Map<String, Object> data = yaml.load(in);
                products = (List<Map<String, Object>>) data.get("products");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPrice(String productName) {
        if (products == null) {
            return "Каталог не завантажено!";
        }

        for (Map<String, Object> product : products) {
            String name = (String) product.get("name");
            Double price = (Double) product.get("price");

            if (name.equalsIgnoreCase(productName)) {
                return name + " – " + price + " грн";
            }
        }
        return "Товар не знайдено!";
    }
}
