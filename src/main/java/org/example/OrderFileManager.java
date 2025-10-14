package org.example;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderFileManager {

    private static final String FILE_PATH = "src/main/resources/orders.yml";

    private static Yaml getYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(List.class, loaderOptions);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Representer representer = new Representer(dumperOptions);

        return new Yaml(constructor, representer, dumperOptions);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadOrders() {
        try (InputStream input = new FileInputStream(FILE_PATH)) {
            Yaml yaml = getYaml();
            Object data = yaml.load(input);
            if (data == null) return new ArrayList<>();
            System.out.println("[OrderFileManager] Orders loaded: " + ((List<?>) data).size()); // Лог
            return (List<Map<String, Object>>) data;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static void saveOrders(List<Map<String, Object>> orders) {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            Yaml yaml = getYaml();
            yaml.dump(orders, writer);
            System.out.println("[OrderFileManager] Orders saved: " + orders.size()); // Лог
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Додати нове замовлення
    public static void addOrder(Map<String, Object> orderData) {
        List<Map<String, Object>> orders = loadOrders();
        if (orders == null) orders = new ArrayList<>();
        orders.add(orderData);
        saveOrders(orders);
        System.out.println("[OrderFileManager] New order added: " + orderData.get("orderCode"));
    }

    // Оновити статус замовлення
    public static void updateOrderStatus(String orderCode, String status, String comment) {
        List<Map<String, Object>> orders = loadOrders();
        for (Map<String, Object> order : orders) {
            if (order.get("orderCode").toString().equals(orderCode)) {
                order.put("status", status);
                order.put("comment", comment);
                break;
            }
        }
        saveOrders(orders);
    }

    // Видалити замовлення
    public static void deleteOrder(String orderCode) {
        List<Map<String, Object>> orders = loadOrders();
        orders.removeIf(order -> order.get("orderCode").toString().equals(orderCode));
        saveOrders(orders);
    }

    // ✅ Новий метод: отримати всі замовлення (з типом доставки)
    public static List<Map<String, Object>> getOrders() {
        return loadOrders();
    }
}
