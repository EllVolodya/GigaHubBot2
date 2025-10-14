package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CatalogManager {

    private static final String CATALOG_PATH = "app/catalog.yml"; // ваш шлях до файлу

    // 🔹 Завантаження YAML
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

    // 🔹 Збереження YAML
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
}
