package org.example;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InviteManager {

    // --- Додати інвайт
    public static boolean addInvite(String name, String kasa, String city, String botUsername) {
        String inviteCode = UUID.randomUUID().toString().substring(0, 8);
        String inviteLink = "https://t.me/" + botUsername + "?start=" + inviteCode;

        String sql = "INSERT INTO invites (name, kasa, city, invite, number) VALUES (?, ?, ?, ?, 0)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, kasa);
            stmt.setString(3, city);
            stmt.setString(4, inviteLink);

            stmt.executeUpdate();
            System.out.println("✅ Invite added: " + inviteLink);
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Отримати всі інвайти
    public static Map<Integer, Map<String, Object>> getAllInvites() {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        String sql = "SELECT * FROM invites ORDER BY id ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> data = new LinkedHashMap<>();
                int id = rs.getInt("id");
                data.put("name", rs.getString("name"));
                data.put("kasa", rs.getString("kasa"));
                data.put("city", rs.getString("city"));
                data.put("invite", rs.getString("invite"));
                data.put("number", rs.getInt("number")); // актуальний лічильник
                result.put(id, data);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    // --- Збільшити лічильник number при переході по інвайту
    public static boolean incrementInviteNumber(String inviteCode) {
        String sql = "UPDATE invites SET number = number + 1 WHERE invite LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + inviteCode + "%");
            int updated = stmt.executeUpdate();
            return updated > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Отримати інвайт по коду
    public static Map<String, Object> getInviteByCode(String inviteCode) {
        String sql = "SELECT * FROM invites WHERE invite LIKE ? LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + inviteCode + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", rs.getInt("id"));
                data.put("name", rs.getString("name"));
                data.put("kasa", rs.getString("kasa"));
                data.put("city", rs.getString("city"));
                data.put("invite", rs.getString("invite"));
                data.put("number", rs.getInt("number"));
                return data;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- Видалити інвайт по ID
    public static boolean deleteInvite(int id) {
        String sql = "DELETE FROM invites WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            int deleted = stmt.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Редагувати інвайт по ID
    public static boolean editInvite(int id, String name, String kasa, String city) {
        String sql = "UPDATE invites SET name = ?, kasa = ?, city = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, kasa);
            stmt.setString(3, city);
            stmt.setInt(4, id);
            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

}
