package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HitsManager {

    private static final String RESOURCE_FOLDER = "src/main/resources";
    private static final String FILE_PATH = RESOURCE_FOLDER + "/hits.yml";

    private static final Yaml YAML;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED); // щоб рядки обгортались у лапки
        YAML = new Yaml(options); // тут просто передаємо DumperOptions

        initFile();
    }

    private static void initFile() {
        try {
            File folder = new File(RESOURCE_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            File file = new File(FILE_PATH);
            if (!file.exists()) {
                file.createNewFile();
                // Записуємо порожній список
                FileWriter writer = new FileWriter(file);
                YAML.dump(new ArrayList<>(), writer);
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadHits() {
        try (FileReader reader = new FileReader(FILE_PATH)) {
            Object obj = YAML.load(reader);
            if (obj instanceof List) {
                return (List<Map<String, Object>>) obj;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void saveHit(String title, String description, String media) {
        try {
            List<Map<String, Object>> hits = loadHits();

            int nextId = hits.size() + 1;

            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("id", nextId);
            hit.put("title", title);
            hit.put("description", description);
            hit.put("media", media != null ? media : "немає"); // 👈 завжди є поле

            hits.add(hit);

            FileWriter writer = new FileWriter(FILE_PATH);
            YAML.dump(hits, writer);
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}