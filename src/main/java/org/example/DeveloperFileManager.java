package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.*;

public class DeveloperFileManager {

    private static final String RESOURCE_FOLDER = "src/main/resources";
    private static final String INVITES_FILE = RESOURCE_FOLDER + "/invites.yml";
    private static final String ORDERS_FILE = RESOURCE_FOLDER + "/orders.yml";
    private static final String CHANGELOG_FILE = RESOURCE_FOLDER + "/changelog.yml";

    static {
        try {
            File folder = new File(RESOURCE_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            new File(INVITES_FILE).createNewFile();
            new File(ORDERS_FILE).createNewFile();
            new File(CHANGELOG_FILE).createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Yaml getYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(List.class, loaderOptions);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Representer representer = new Representer(dumperOptions);
        return new Yaml(constructor, representer, dumperOptions);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadInvites() {
        try (InputStream input = new FileInputStream(INVITES_FILE)) {
            Yaml yaml = getYaml();
            Object data = yaml.load(input);
            if (data == null) return new ArrayList<>();
            return (List<Map<String, Object>>) data;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static void saveInvites(List<Map<String, Object>> invites) {
        try (FileWriter writer = new FileWriter(INVITES_FILE)) {
            Yaml yaml = getYaml();
            yaml.dump(invites, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Робота з запрошеннями ---
    public static Map<String, Object> addInvite(String name, String kasa, String city, String botUsername) {
        List<Map<String, Object>> invites = loadInvites();
        int nextId = invites.stream()
                .mapToInt(i -> (int) i.getOrDefault("id", 0))
                .max()
                .orElse(0) + 1;

        String inviteCode = UUID.randomUUID().toString().substring(0, 8);
        String inviteLink = "https://t.me/" + botUsername + "?start=" + inviteCode;

        Map<String, Object> newInvite = new LinkedHashMap<>();
        newInvite.put("id", nextId);
        newInvite.put("kasa", kasa);
        newInvite.put("name", name);
        newInvite.put("invite", inviteLink);
        newInvite.put("number", 0);
        newInvite.put("city", city);

        invites.add(newInvite);
        saveInvites(invites);

        return newInvite;
    }

    public static boolean deleteInvite(int id) {
        List<Map<String, Object>> invites = loadInvites();
        boolean removed = invites.removeIf(inv -> inv.get("id") != null && (int) inv.get("id") == id);
        saveInvites(invites);
        return removed;
    }

    public static boolean editInvite(int id, String name, String kasa, String city) {
        List<Map<String, Object>> invites = loadInvites();
        for (Map<String, Object> inv : invites) {
            if ((int) inv.getOrDefault("id", -1) == id) {
                inv.put("name", name);
                inv.put("kasa", kasa);
                inv.put("city", city);
                saveInvites(invites);
                return true;
            }
        }
        return false;
    }

    public static Map<Integer, Map<String, Object>> getAllInvites() {
        List<Map<String, Object>> invites = loadInvites();
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> i : invites) {
            int id = (int) i.get("id");
            result.put(id, i);
        }
        return result;
    }

    public static Map<String, Object> getInviteById(int id) {
        return getAllInvites().get(id);
    }

    public static int getNoInviteUsersCount() {
        // Можна реалізувати з логів або окремого файлу
        return 0;
    }

    // --- Робота із замовленнями ---
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadOrders() {
        try (InputStream input = new FileInputStream(ORDERS_FILE)) {
            Yaml yaml = getYaml();
            Object data = yaml.load(input);
            if (data == null) return new ArrayList<>();
            return (List<Map<String, Object>>) data;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static Map<String, Integer> getOrdersSummary() {
        List<Map<String, Object>> orders = loadOrders();
        int total = orders.size();
        int sent = (int) orders.stream().filter(o -> "Підтверджено".equals(o.get("status"))).count();
        int rejected = (int) orders.stream().filter(o -> "Відхилено".equals(o.get("status"))).count();
        Map<String, Integer> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("sent", sent);
        summary.put("rejected", rejected);
        return summary;
    }

    public static Map<String, Object> getOrderById(String orderCode) {
        List<Map<String, Object>> orders = loadOrders();
        for (Map<String, Object> o : orders) {
            if (orderCode.equals(o.get("orderCode"))) return o;
        }
        return null;
    }

    public static List<String> getChangelog() {
        try (InputStream input = new FileInputStream(CHANGELOG_FILE)) {
            Yaml yaml = getYaml();
            Object data = yaml.load(input);
            if (data == null) return new ArrayList<>();
            return (List<String>) data;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static List<Map<String, String>> getRejectedOrders() {
        List<Map<String, String>> rejected = new ArrayList<>();

        try {
            List<Map<String, Object>> orders = loadOrders();

            for (Map<String, Object> order : orders) {
                String status = String.valueOf(order.get("status"));
                if ("rejected".equalsIgnoreCase(status)) {
                    Map<String, String> info = new HashMap<>();
                    info.put("orderCode", String.valueOf(order.getOrDefault("orderCode", "—")));
                    info.put("comment", String.valueOf(order.getOrDefault("comment", "")));
                    rejected.add(info);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return rejected;
    }

    public static List<Long> getNoInviteUsers() {
        List<Long> result = new ArrayList<>();

        try (InputStream input = DeveloperFileManager.class
                .getClassLoader()
                .getResourceAsStream("registered_users.yml")) {

            if (input == null) {
                System.out.println("❌ Файл registered_users.yml не знайдено у ресурсах!");
                return result; // повертаємо порожній список
            }

            Yaml yaml = new Yaml();
            Object data = yaml.load(input);

            if (data instanceof Set) {
                Set<?> users = (Set<?>) data;
                for (Object idObj : users) {
                    try {
                        Long id = Long.parseLong(idObj.toString());
                        result.add(id);
                    } catch (NumberFormatException e) {
                        System.out.println("Невірний ID користувача: " + idObj);
                    }
                }
            } else {
                System.out.println("❌ Очікував Set, але отримано: " + data.getClass());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
