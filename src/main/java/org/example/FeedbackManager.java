package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

public class FeedbackManager {

    private static final String FILE_PATH = "feedbacks.yml";

    private static Map<Long, List<String>> feedbacks = new HashMap<>();

    // Завантаження відгуків з файлу YAML
    public static void loadFeedbacks() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try (InputStream input = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<Long, List<String>> map = yaml.load(input);
            if (map != null) feedbacks = map;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Збереження всіх відгуків у YAML
    public static void saveFeedbacks() {
        try (Writer writer = new FileWriter(FILE_PATH)) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            yaml.dump(feedbacks, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Додає новий відгук
    public static void addFeedback(Long userId, String reviewText) {
        feedbacks.computeIfAbsent(userId, k -> new ArrayList<>()).add(reviewText);
        saveFeedbacks();
    }

    // Видаляє останній відгук користувача
    public static void removeLastFeedback(Long userId) {
        List<String> reviews = feedbacks.get(userId);
        if (reviews != null && !reviews.isEmpty()) {
            reviews.remove(reviews.size() - 1);
            if (reviews.isEmpty()) feedbacks.remove(userId);
            saveFeedbacks();
        }
    }

    // Видаляє всі відгуки користувача
    public static void removeAllFeedbacks(Long userId) {
        feedbacks.remove(userId);
        saveFeedbacks();
    }

    // Повертає останній відгук користувача
    public static String getLastFeedback(Long userId) {
        List<String> reviews = feedbacks.get(userId);
        if (reviews == null || reviews.isEmpty()) return null;
        return reviews.get(reviews.size() - 1);
    }

    // Повертає всі відгуки конкретного користувача
    public static List<String> getAllUserFeedbacks(Long userId) {
        return feedbacks.getOrDefault(userId, new ArrayList<>());
    }

    // Повертає всі відгуки всіх користувачів
    public static Map<Long, List<String>> getAllFeedbacks() {
        return feedbacks;
    }
}
