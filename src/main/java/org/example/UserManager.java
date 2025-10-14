package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class UserManager {
    // Безпечний шлях: створюємо папку data у корені проекту або контейнера
    private static final String FILE_PATH = "data/registered_users.yml";
    private Set<Long> registeredUsers = new HashSet<>();

    public UserManager() {
        loadUsers();
    }

    // Завантаження користувачів із YAML
    private void loadUsers() {
        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                Yaml yaml = new Yaml();
                Set<?> loaded = yaml.loadAs(input, Set.class);
                if (loaded != null) {
                    registeredUsers = new HashSet<>();
                    for (Object obj : loaded) {
                        if (obj instanceof Number) {
                            registeredUsers.add(((Number) obj).longValue());
                        } else if (obj instanceof String) {
                            registeredUsers.add(Long.parseLong((String) obj));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Збереження користувачів у YAML
    private void saveUsers() {
        try {
            File file = new File(FILE_PATH);
            // Створюємо всі проміжні папки, якщо їх нема
            file.getParentFile().mkdirs();

            try (Writer writer = new FileWriter(file)) {
                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                Yaml yaml = new Yaml(options);
                yaml.dump(registeredUsers, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Додавання нового користувача
    public void registerUser(Long chatId) {
        if (registeredUsers.add(chatId)) {
            saveUsers();
        }
    }

    // Отримати всіх користувачів
    public Set<Long> getRegisteredUsers() {
        return registeredUsers;
    }
}
