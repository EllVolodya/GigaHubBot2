package org.example;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogService {
    private static final String CATALOG_PATH = "src/main/resources/catalog.yml";

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

    public static List<Map<String, Object>> getFlatProductList(Map<String, Object> catalog) {
        List<Map<String, Object>> flatList = new ArrayList<>();
        if (!catalog.containsKey("catalog")) return flatList;
        List<Map<String, Object>> categories = (List<Map<String, Object>>) catalog.get("catalog");
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
}