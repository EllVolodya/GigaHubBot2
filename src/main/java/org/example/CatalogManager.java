package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogManager {

    private static final String CATALOG_RESOURCE = "catalog.yml"; // Файл має лежати в src/main/resources/

    // 🔹 Завантаження YAML з resources
    public static Map<String, Object> loadCatalog() {
        Yaml yaml = new Yaml();
        try (InputStream in = CatalogManager.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE);
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {

            Object loaded = yaml.load(reader);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (NullPointerException e) {
            System.out.println("[YAML] Файл catalog.yml не знайдений у resources!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new LinkedHashMap<>();
    }

    // 🔹 Збереження YAML (тільки якщо реально хочеш перезаписувати ресурс)
    public static void saveCatalog(Map<String, Object> data) {
        // На Railway/в resources зазвичай не можна записувати, тому тут можна просто виводити або робити backup у зовнішній файл
        System.out.println("[YAML] Збереження каталогу в ресурсах неможливе. Для редагування треба робити зовнішній файл.");
    }
}
