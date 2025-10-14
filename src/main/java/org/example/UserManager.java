package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class UserManager {
    private static final String FILE_PATH = "src/main/resources/registered_users.yml";
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
                // Використовуємо loadAs і Set.class
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
        try (Writer writer = new FileWriter(FILE_PATH)) {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            yaml.dump(registeredUsers, writer);
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
