package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class StoreBot extends TelegramLongPollingBot {

    private final String botUsername = "GigaHubAssistant_bot";

    // 🔹 Користувацькі стани
    private final Map<Long, String> currentCategory = new HashMap<>();
    private final Map<Long, String> currentSubcategory = new HashMap<>();
    private final Map<Long, Integer> productIndex = new HashMap<>();
    private final Map<Long, List<Map<String, Object>>> userCart = new HashMap<>();

    //Права
    private final List<Long> ADMINS = List.of(620298889L, 533570832L,1030917576L);
    private final List<Long> DEVELOPERS = List.of(620298889L, 533570832L,1030917576L); // тут айді розробників

    // 🔹 Адмінські стани
    private Map<Long, Long> adminReplyTarget = new HashMap<>();

    private final Map<Long, String> adminEditingProduct = new HashMap<>();
    private final Map<Long, String> adminEditingField = new HashMap<>();
    private final Map<Long, List<String>> adminMatchList = new HashMap<>();
    private final Map<Long, String> adminNewCategory = new HashMap<>();
    private final Map<Long, String> userStates = new HashMap<>();
    private final List<String> hitItems = new ArrayList<>();
    private final Map<Long, List<String>> supportAnswers = new HashMap<>();
    private final Map<Long, List<Map<String, Object>>> userOrders = new HashMap<>();
    private final Map<Long, Map<String, Object>> lastShownProduct = new HashMap<>();
    private final Map<Long, Integer> adminOrderIndex = new HashMap<>();
    private final Map<String, Object> tempStorage = new HashMap<>();

    private final CatalogSearcher catalogSearcher = new CatalogSearcher();
    private final Map<Long, List<Map<String, Object>>> searchResults = new HashMap<>();

    private final Map<Long, List<String>> feedbacks = new HashMap<>();
    private final Map<Long, Long> replyTargets = new HashMap<>();

    public StoreBot(String botToken) {
        super(botToken);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Long userId = update.getMessage().getFrom().getId();
        String chatId = update.getMessage().getChatId().toString();
        String state = userStates.get(userId);
        String text = update.getMessage().hasText() ? update.getMessage().getText().trim() : null;

        if ("awaiting_photo".equals(state)) {
            if (update.getMessage().hasPhoto()) {
                List<PhotoSize> photos = update.getMessage().getPhoto();
                System.out.println("[PHOTO] Отримано фото від userId=" + userId + ", кількість розмірів: " + photos.size());
                handleAwaitingPhoto(userId, chatId, photos);
            } else {
                sendText(chatId, "❌ Будь ласка, надішліть фото, а не текст.");
            }
            return;
        }

        // 🔹 Якщо користувач у стані – передаємо в handleState
        if (state != null) {
            try {
                handleFeedbackState(userId, chatId, text, state);
                handleState(userId, chatId, text, state, update);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                sendText(chatId, "❌ Сталася помилка при обробці вашого запиту.");
            }
            return;
        }

        try {
            // 🔹 Обробка станів користувача
            if (state != null) {
                switch (state) {
                    case "awaiting_pickup_data" -> {
                        List<Map<String, Object>> cart = userCart.get(userId);
                        if (cart == null || cart.isEmpty()) {
                            sendText(chatId, "🛒 Ваш кошик порожній.");
                            userStates.remove(userId);
                            return;
                        }

                        String orderCode = String.format("%04d", new Random().nextInt(10000));
                        Map<String, Object> orderData = new HashMap<>();
                        orderData.put("orderCode", orderCode);
                        orderData.put("pickupData", text);
                        orderData.put("items", new ArrayList<>(cart));
                        double total = cart.stream()
                                .mapToDouble(i -> Double.parseDouble(i.getOrDefault("price","0").toString()))
                                .sum();
                        orderData.put("total", total);
                        orderData.put("status", "Нове");
                        orderData.put("date", LocalDateTime.now().toString());
                        orderData.put("type", "pickup");

                        userOrders.computeIfAbsent(userId, k -> new ArrayList<>()).add(orderData);
                        OrderFileManager.addOrder(orderData);

                        // 🔹 Повідомлення адміну
                        for (Long adminId : ADMINS) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("🏬 *Самовивіз*\n");
                            sb.append("🆔 User ID: ").append(userId).append("\n");
                            sb.append("🔢 Код замовлення: ").append(orderCode).append("\n");
                            sb.append("📋 Дані:\n").append(text).append("\n\n");
                            for (Map<String,Object> item : cart) {
                                sb.append("• ").append(item.get("title")).append(" — ").append(item.get("price")).append(" грн\n");
                            }
                            sb.append("\n💰 Всього: ").append(total).append(" грн");
                            sendText(adminId.toString(), sb.toString());
                        }

                        userCart.remove(userId);
                        userStates.remove(userId);
                        sendText(chatId, "✅ Ваше замовлення на самовивіз успішно оформлено!\nКод замовлення: " + orderCode);
                    }

                    case "awaiting_manufacturer" -> {
                        // 1️⃣ Беремо назву товару з тимчасового сховища
                        String productName = (String) tempStorage.get(userId + "_editingProduct");
                        if (productName == null) {
                            sendText(chatId, "❌ Не знайдено товар для редагування.");
                            userStates.put(userId, "admin_menu");
                            return;
                        }

                        // 2️⃣ Оновлюємо виробника через CatalogEditor
                        String input = text.trim();
                        try {
                            CatalogEditor.updateProductManufacturer(productName, input);
                            if (input.equalsIgnoreCase("❌") || input.isEmpty()) {
                                sendText(chatId, "✅ Виробник видалено для товару: " + productName);
                            } else {
                                sendText(chatId, "✅ Виробник збережено: " + input);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            sendText(chatId, "⚠️ Помилка при оновленні catalog.yml: " + e.getMessage());
                        }

                        // 3️⃣ Повертаємо користувача в меню редагування
                        sendText(chatId, createEditMenu(chatId, productName).getText());
                        userStates.put(userId, "edit_product");
                    }
                }
            }

            // 🔹 Основні команди (кнопки)
            if (text == null) return;

            switch (text) {
                case "/start" -> {
                    clearUserState(userId);

                    // Отримуємо chatId як Long
                    Long chatIdLong = update.getMessage().getChatId();
                    String chatIdStr = chatIdLong.toString(); // для createUserMenu, якщо потрібен String

                    String inviteCode = null;

                    // Перевіряємо, чи є параметр invite після пробілу
                    if (text != null && text.contains(" ")) {
                        String[] parts = text.split(" ");
                        if (parts.length > 1 && !parts[1].isBlank()) { // другий елемент існує і не порожній
                            inviteCode = parts[1].trim();

                            // Збільшуємо лічильник number для цього invite
                            if (InviteManager.incrementInviteNumber(inviteCode)) {
                                System.out.println("✅ Лічильник number для invite " + inviteCode + " збільшено.");
                            } else {
                                System.out.println("❌ Invite не знайдено: " + inviteCode);
                            }
                        }
                    }

                    // Додаємо користувача у REGISTERED_USERS
                    UserManager userManager = new UserManager();
                    userManager.registerUser(chatIdLong); // передаємо Long

                    // Відправка головного меню
                    sendMessage(createUserMenu(chatIdStr, userId));

                    System.out.println("Користувач натиснув /start: " + chatIdLong +
                            (inviteCode != null ? " (Invite: " + inviteCode + ")" : ""));
                }

                case "🧱 Каталог товарів" -> sendCategories(userId);
                case "📋 Кошик" -> {
                    try {
                        showCart(userId);  // userId — Long
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
                case "🛒 Перейти в кошик" -> {
                    try {
                        showCart(userId);  // userId — Long
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
                case "🧹 Очистити кошик" -> clearCart(userId);
                case "⬅ Назад", "⬅️ Назад" -> {
                    clearUserState(userId);
                    sendMessage(createUserMenu(chatId, userId));
                }
                case "➡ Далі" -> showNextProduct(userId);
                case "🛒 Додати в кошик" -> addToCart(userId);
                case "📍 Адреси та Контакти" -> {
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setParseMode("HTML");
                    message.setDisableWebPagePreview(true); // ⬅ вимикає прев’ю
                    message.setText(
                            "🏘️ Казанка: <a href=\"https://maps.app.goo.gl/d7GQnKaXedkHDuq97\">на мапі</a>\n" +
                                    "📞 Телефон: <code>(050) 457 84 58</code>\n\n" +
                                    "🏘️ Новий Буг: <a href=\"https://maps.app.goo.gl/YJ5qzxAqXVpZJXYPA\">на мапі</a>\n" +
                                    "📞 Телефон: <code>(050) 493 15 15</code>"
                    );
                    execute(message);
                }

                case "🌐 Соц-мережі" -> {
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId);
                    message.setParseMode("HTML");
                    message.setDisableWebPagePreview(true); // ⬅ вимикає прев’ю
                    message.setText(
                            "🌐 Ми у соціальних мережах:\n\n" +
                                    "📘 Facebook: <a href=\"https://www.facebook.com/p/%D0%93%D0%B8%D0%B3%D0%B0%D1%85%D0%B0%D0%B1-61578183892871/\">відкрити</a>\n" +
                                    "📸 Instagram: <a href=\"https://www.instagram.com/_gigahub_?igsh=Y211bWRqazhhcmtu&utm_source=qr\">відкрити</a>\n" +
                                    "🎵 TikTok: <a href=\"tiktok.com/@gigahub2\">відкрити</a>\n\n" +
                                    "☕ Також Instagram доступний у CoffeeMax: <a href=\"https://www.instagram.com/coffee_max_1?igsh=bmhsNDRyN2M5eG5l&utm_source=qr\">відкрити</a>"
                    );
                    execute(message);
                }
                case "💬 Допомога" -> sendMessage(createHelpMenu(chatId));
                case "✉️ Написати консультанту" -> {
                    userStates.put(userId, "ask_consultant");
                    sendText(chatId, "✏️ Напишіть своє питання консультанту:");
                }
                case "💌 Відповіді" -> {
                    List<String> answers = supportAnswers.get(userId);
                    String reply = (answers == null || answers.isEmpty())
                            ? "Поки що немає відповідей від консультантів."
                            : "💌 Відповіді консультантів:\n\n" + String.join("\n\n", answers);
                    sendText(chatId, reply);
                }

                case "🔍 Пошук товару" -> {
                    userStates.put(userId, "waiting_for_search");
                    sendText(chatId, "🔎 Введіть назву товару, який хочете знайти:");
                    System.out.println("🟢 User " + userId + " встановив стан waiting_for_search");
                    return;
                }

                case "🛒 Замовити товар" -> {
                    List<Map<String, Object>> cart = userCart.get(userId);
                    if (cart == null || cart.isEmpty()) {
                        sendText(chatId, "🛒 Ваш кошик порожній.");
                        return;
                    }

                    ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
                    markup.setResizeKeyboard(true);

                    KeyboardRow deliveryRow = new KeyboardRow();
                    deliveryRow.add(new KeyboardButton("🏬 Самовивіз"));
                    deliveryRow.add(new KeyboardButton("📦 Доставка по місту"));
                    deliveryRow.add(new KeyboardButton("📮 Доставка Новою поштою"));

                    KeyboardRow backRow = new KeyboardRow();
                    backRow.add(new KeyboardButton("⬅️ Назад"));

                    markup.setKeyboard(List.of(deliveryRow, backRow));

                    sendMessage(chatId, "Оберіть спосіб доставки:", markup);
                    userStates.put(userId, "awaiting_delivery_choice");
                }

                case "🏬 Самовивіз" -> {
                    tempStorage.put(userId + "_deliveryType", "Самовивіз");
                    userStates.put(userId, "order_pickup");

                    sendText(chatId,
                            "✏️ Введіть, будь-ласка, свої дані для самовивозу у форматі:\n" +
                                    "🏙 Місто\n👤 П.І.\n📞 Телефон\n💳 Номер картки (Магазину)\n\n" +
                                    "📌 Приклад:\n" +
                                    "Казанка, Сидоренко Олена Олексіївна, +380631234567, 4444"
                    );
                }

                case "📦 Доставка по місту" -> {
                    tempStorage.put(userId + "_deliveryType", "Доставка по місту");
                    userStates.put(userId, "awaiting_city_delivery");
                    sendText(chatId,
                            "📝 Введіть, будь-ласка, дані для доставки по місту у форматі:\n" +
                                    "📍 Адреса, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                    "📌 Приклад:\n" +
                                    "вул. Шевченка 10, Казанка, Петров Петро Петрович, +380671234567, 4444");
                }

                case "📮 Доставка Новою поштою" -> {
                    tempStorage.put(userId + "_deliveryType", "Нова Пошта");
                    userStates.put(userId, "awaiting_post_delivery");
                    sendText(chatId,
                            "📝 Введіть, будь-ласка, дані для доставки Новою Поштою у форматі:\n" +
                                    "📮 Відділення НП, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                    "📌 Приклад:\n" +
                                    "№12, Іваненко Іван Іванович, +380501234567, 4444");
                }

                case "⬅️ Назад до кошика" -> {
                    showCart(userId); // повертаємо користувача у кошик
                }

                case "🎯 Хіт продажу" -> {
                    List<Map<String, Object>> hits = HitsManager.loadHits();
                    if (hits.isEmpty()) {
                        sendText(chatId, "❌ Поки що немає хітів продажу.");
                        return;
                    }

                    for (Map<String, Object> hit : hits) {
                        String title = hit.get("title") != null ? hit.get("title").toString() : "";
                        String description = hit.get("description") != null ? hit.get("description").toString() : "";

                        // Формуємо текст для повідомлення
                        String textMsg = "";
                        if (!title.isEmpty()) textMsg += "⭐ *" + title + "*";
                        if (!description.isEmpty() && !"немає".equals(description)) {
                            if (!textMsg.isEmpty()) textMsg += "\n\n";
                            textMsg += description;
                        }

                        // Якщо текст порожній і є медіа, залишаємо caption пустим
                        Object mediaObj = hit.get("media");
                        String caption = textMsg;
                        if (caption.isEmpty() && mediaObj != null && !"немає".equals(mediaObj.toString())) {
                            caption = null; // порожній підпис для фото/відео
                        } else if (caption.isEmpty()) {
                            caption = "немає"; // для випадків, коли немає і медіа
                        }

                        if (mediaObj != null && !"немає".equals(mediaObj.toString())) {
                            String fileId = mediaObj.toString();
                            try {
                                if (fileId.startsWith("BAAC")) { // відео
                                    SendVideo video = SendVideo.builder()
                                            .chatId(chatId)
                                            .video(new InputFile(fileId))
                                            .caption(caption)
                                            .parseMode("Markdown")
                                            .build();
                                    execute(video);
                                } else { // фото
                                    SendPhoto photo = SendPhoto.builder()
                                            .chatId(chatId)
                                            .photo(new InputFile(fileId))
                                            .caption(caption)
                                            .parseMode("Markdown")
                                            .build();
                                    execute(photo);
                                }
                            } catch (TelegramApiException e) {
                                e.printStackTrace();
                                sendText(chatId, "❌ Не вдалося надіслати медіа.");
                            }
                        } else {
                            sendText(chatId, caption);
                        }
                    }
                }

                // Меню розробника
                case "👨‍💻 Меню розробника" -> {
                    if (DEVELOPERS.contains(userId)) {
                        sendMessage(createDeveloperMenu(chatId));
                    } else {
                        sendText(chatId, "⛔ У вас немає доступу.");
                    }
                }

                case "🔗 Запрошувальні посилання" -> {
                    if (DEVELOPERS.contains(userId)) {
                        userStates.put(userId, "invites_menu");
                        sendMessage(createInvitesMenu(chatId));
                    } else sendText(chatId, "⛔ У вас немає доступу.");
                }

                case "📜 Логирування" -> {
                    if (DEVELOPERS.contains(userId)) sendMessage(createLogsMenu(chatId));
                    else sendText(chatId, "⛔ У вас немає доступу.");
                }

                case "📝 Список онови" -> {
                    if (DEVELOPERS.contains(userId)) {
                        List<String> updates = DeveloperFileManager.getChangelog();
                        if (updates.isEmpty()) sendText(chatId, "📝 Список оновлень поки порожній.");
                        else sendText(chatId, "📝 Список оновлень:\n\n" + String.join("\n\n", updates));
                    } else sendText(chatId, "⛔ У вас немає доступу.");
                }

                case "📊 Статистика запрошувань" -> {
                    userStates.put(userId, "logs_invites");
                    handleState(userId, chatId, text, "logs_invites", update);
                }

                case "📊 Статистика без запрошень" -> {
                    userStates.put(userId, "logs_no_invite");
                    handleState(userId, chatId, text, "logs_no_invite", update);
                }

                case "📦 Замовлення" -> {
                    userStates.put(userId, "logs_orders");
                    handleState(userId, chatId, text, "logs_orders", update);
                }

                case "⬅️ Назад в розробника" -> {
                    if (DEVELOPERS.contains(userId)) {
                        sendMessage(createDeveloperMenu(chatId));
                    } else sendText(chatId, "⛔ У вас немає доступу.");
                }

                // Адмін меню
                case "⚙️ Продавца меню" -> {
                    if (ADMINS.contains(userId)) sendMessage(createAdminMenu(chatId));
                    else sendText(chatId, "⛔ У вас немає доступу.");
                }
                case "✏️ Редагувати товар" -> {
                    if (ADMINS.contains(userId)) {
                        userStates.put(userId, "edit_product");
                        sendText(chatId, "✏️ Введіть назву товару, який хочете редагувати:");
                    } else sendText(chatId, "⛔ У вас немає прав.");
                }
                case "Редагувати категорії" -> {
                    if (ADMINS.contains(userId)) {
                        userStates.put(userId, "category_management");
                        sendMessage(createCategoryAdminMenu(chatId));
                    } else sendText(chatId, "⛔ У вас немає доступу до цієї функції.");
                }

                case "🛒 Замовлення користувачів" -> {
                    try (Connection conn = DatabaseManager.getConnection()) {
                        // Перевіряємо, чи є замовлення в базі
                        String countSql = "SELECT COUNT(*) FROM orders";
                        try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                            ResultSet countRs = countStmt.executeQuery();
                            if (countRs.next() && countRs.getInt(1) == 0) {
                                sendText(chatId, "Поки що немає замовлень.");
                                return;
                            }
                        }

                        // Зберігаємо, що адмін зараз дивиться перше замовлення
                        adminOrderIndex.put(userId, 0);

                        // Показуємо перше замовлення з бази
                        showAdminOrder(userId, chatId);

                    } catch (SQLException e) {
                        e.printStackTrace();
                        sendText(chatId, "❌ Помилка при завантаженні замовлень з бази.");
                    }
                }

                case "✅ Підтвердити" -> {
                    try (Connection conn = DatabaseManager.getConnection()) {
                        // Витягаємо перше нове замовлення
                        String selectSql = "SELECT * FROM orders WHERE status = 'Нове' ORDER BY id ASC LIMIT 1";
                        try (PreparedStatement stmt = conn.prepareStatement(selectSql);
                             ResultSet rs = stmt.executeQuery()) {

                            if (!rs.next()) {
                                sendText(chatId, "Замовлень немає.");
                                break;
                            }

                            Long orderId = rs.getLong("id");
                            String orderCode = rs.getString("orderCode");
                            Long orderUserId = rs.getLong("userId");

                            sendText(orderUserId.toString(), "✅ Ваше замовлення підтверджено! Очікуйте доставку.");

                            String updateSql = "UPDATE orders SET status = 'Підтверджено' WHERE id = ?";
                            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                                updateStmt.setLong(1, orderId);
                                updateStmt.executeUpdate();
                            }

                            sendText(chatId, "Замовлення підтверджено ✅");

                            // Показуємо адміну наступне замовлення
                            Long adminId = userId;
                            showAdminOrder(adminId, chatId);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        sendText(chatId, "❌ Помилка при підтвердженні замовлення.");
                    }
                }

                case "❌ Відхилити" -> {
                    userStates.put(userId, "reject_order_reason");
                    sendText(chatId, "✏️ Введіть причину відхилення замовлення:");
                }

                case "🗑️ Видалити замовлення" -> {
                    try (Connection conn = DatabaseManager.getConnection()) {
                        // Беремо перше активне замовлення
                        String selectSql = "SELECT * FROM orders WHERE status NOT IN ('Видалено', 'Підтверджено', 'Відхилено') ORDER BY id ASC LIMIT 1";
                        try (PreparedStatement stmt = conn.prepareStatement(selectSql);
                             ResultSet rs = stmt.executeQuery()) {

                            if (!rs.isBeforeFirst()) {
                                sendText(chatId, "Замовлень немає.");
                                break;
                            }

                            if (rs.next()) {
                                String orderCode = rs.getString("orderCode");
                                Long orderUserId = rs.getLong("userId");

                                // Оновлюємо статус на "Видалено"
                                String updateSql = "UPDATE orders SET status = ?, comment = ? WHERE orderCode = ?";
                                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                                    updateStmt.setString(1, "Видалено");
                                    updateStmt.setString(2, "Видалено адміністратором");
                                    updateStmt.setString(3, orderCode);
                                    updateStmt.executeUpdate();
                                }

                                // Повідомляємо користувачу
                                sendText(orderUserId.toString(), "🗑️ Ваше замовлення було видалено адміністратором.");

                                // Повідомляємо адміну
                                sendText(chatId, "🗑️ Замовлення видалено.");

                                // Показуємо наступне активне замовлення
                                showAdminOrder(userId, chatId);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        sendText(chatId, "❌ Помилка при видаленні замовлення.");
                    }
                }

                case "⏭️ Дальше" -> {
                    int idx = adminOrderIndex.getOrDefault(userId, 0);
                    adminOrderIndex.put(userId, idx + 1); // переходимо до наступного
                    showAdminOrder(userId, chatId);
                }
                case "⏮️ Назад" -> {
                    int idx = adminOrderIndex.getOrDefault(userId, 0);
                    if (idx > 0) adminOrderIndex.put(userId, idx - 1); // повертаємося назад
                    showAdminOrder(userId, chatId);
                }

                case "⬅️ Назад (Продавець)" -> {
                    // Очищаємо індекс перегляду замовлень
                    adminOrderIndex.remove(userId);

                    // Відправляємо меню продавця
                    SendMessage menuMsg = createAdminMenu(chatId);
                    try {
                        execute(menuMsg); // <-- відправка повідомлення через execute
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                        sendText(chatId, "❌ Помилка при відправці меню продавця.");
                    }
                }

                case "➡️ Далі" -> {
                    int idx = adminOrderIndex.getOrDefault(userId, 0);
                    idx++;
                    adminOrderIndex.put(userId, idx);
                    showAdminOrder(userId, chatId);
                }

                case "⬅️ Назад в адмін-меню" -> {
                    sendMessage(createAdminMenu(chatId));
                }

                case "⬅️ Назад в головне меню" -> {
                    clearUserState(userId);
                    sendMessage(createUserMenu(chatId, userId));
                }

                case "⭐ Додати товар у Хіт продажу" -> {
                    if (!ADMINS.contains(userId)) {
                        sendText(chatId, "⛔ У вас немає доступу.");
                        break;
                    }
                    userStates.put(userId, "awaiting_hit_type"); // <-- тут треба так
                    sendText(chatId, "Ви хочете додати креатив з описом чи тільки медіа?\nНапишіть 'З описом' або 'Тільки медіа':");
                }

                case "💬 Залишити відгук" -> {
                    userStates.put(userId, "waiting_for_feedback");
                    sendText(chatId, "📝 Напишіть свій відгук, ми обов’язково його переглянемо:");
                }

                case "💬 Відгуки користувачів" -> {
                    if (DEVELOPERS.contains(userId)) {
                        Map<Long, List<String>> allReviews = FeedbackManager.getAllFeedbacks();
                        if (allReviews.isEmpty()) {
                            sendText(chatId, "❌ Відгуків поки що немає.");
                        } else {
                            Long targetId = allReviews.keySet().iterator().next();
                            sendMessage(createFeedbackSubMenu(chatId, targetId));
                        }
                    } else sendText(chatId, "⛔ У вас немає доступу.");
                }

                case "✉️ Відповісти на відгук" -> {
                    userStates.put(userId, "writing_reply");
                    sendText(chatId, "✏️ Введіть відповідь для користувача:");
                }

                case "💾 Зберегти відгук" -> {
                    FeedbackManager.saveFeedbacks();
                    sendText(chatId, "💾 Відгук збережено.");
                }

                case "🧹 Видалити відгук" -> {
                    Long target = adminReplyTarget.get(userId);
                    if (target != null) {
                        FeedbackManager.removeLastFeedback(target);
                        sendText(chatId, "🧹 Останній відгук користувача видалено.");
                    } else {
                        sendText(chatId, "❌ Не знайдено користувача для видалення відгуку.");
                    }
                }

                default -> handleText(userId, text);
            }

            // Якщо користувач пише відгук
            if ("waiting_for_feedback".equals(state)) {
                FeedbackManager.addFeedback(userId, text);
                sendText(chatId, "✅ Ваш відгук надіслано адміністратору!");
                userStates.remove(userId);
                return; // вихід після обробки стану
            }

            if (text.contains("Самовивіз")) {
                System.out.println("DEBUG: Натиснули Самовивіз");
                userStates.put(userId, "order_pickup");
                tempStorage.put(userId + "_deliveryType", "Самовивіз");
                sendText(chatId,
                        "✏️ Введіть, будь-ласка, свої дані для самовивозу у форматі:\n" +
                                "🏙 Місто\n👤 П.І.\n📞 Телефон\n💳 Номер картки (Магазину)\n\n" +
                                "📌 Приклад:\n" +
                                "Казанка, Сидоренко Олена Олексіївна, +380631234567, 4444"
                );
            } else if (text.contains("Доставка по місту")) {
                System.out.println("DEBUG: Натиснули Доставка по місту");
                userStates.put(userId, "awaiting_city_delivery");
                tempStorage.put(userId + "_deliveryType", "Доставка по місту");
                sendText(chatId,
                        "📝 Введіть, будь-ласка, дані для доставки по місту у форматі:\n" +
                                "📍 Адреса, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                "📌 Приклад:\n" +
                                "вул. Шевченка 10, Казанка, Петров Петро Петрович, +380671234567, 4444"
                );
            } else if (text.contains("Нова пошта")) {
                System.out.println("DEBUG: Натиснули Доставка Новою поштою");
                userStates.put(userId, "awaiting_post_delivery");
                tempStorage.put(userId + "_deliveryType", "Нова Пошта");
                sendText(chatId,
                        "📝 Введіть, будь-ласка, дані для доставки Новою Поштою у форматі:\n" +
                                "📮 Відділення НП, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                "📌 Приклад:\n" +
                                "№12, Іваненко Іван Іванович, +380501234567, 4444"
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 Очистити стан користувача
    private void clearUserState(Long chatId) {
        currentCategory.remove(chatId);
        currentSubcategory.remove(chatId);
        productIndex.remove(chatId);
    }

    // 🔹 Категорії
    private void sendCategories(Long chatId) throws TelegramApiException {
        List<String> categories = catalogSearcher.getCategories();

        ReplyKeyboardMarkup markup = ReplyKeyboardMarkup.builder()
                .resizeKeyboard(true)
                .keyboard(buildKeyboard(categories, true))
                .build();

        sendMessage(chatId, "📂 Виберіть категорію:", markup);
    }

    // 🔹 Показ кошика
    private void showCart(Long userId) throws TelegramApiException {
        List<Map<String, Object>> cart = userCart.get(userId);

        if (cart == null || cart.isEmpty()) {
            sendText(userId, "🛒 Ваш кошик порожній!");
            sendMessage(createUserMenu(String.valueOf(userId), userId));
            return;
        }

        StringBuilder sb = new StringBuilder("📋 Ваш кошик:\n\n");
        double total = 0;
        int i = 1;

        for (Map<String, Object> item : cart) {
            String name = item.getOrDefault("name", "Без назви").toString();
            double price = Double.parseDouble(item.getOrDefault("price", "0").toString());
            total += price;
            sb.append(i++).append(". ").append(name).append(" — ").append(price).append(" грн\n");
        }
        sb.append("\n💰 Всього: ").append(total).append(" грн");

        // Клавіатура
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        // Перший рядок – замовити товар + очистити кошик
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🛒 Замовити товар"));
        row1.add(new KeyboardButton("🧹 Очистити кошик"));

        // Другий рядок – назад
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("⬅ Назад"));

        markup.setKeyboard(List.of(row1, row2));

        sendMessage(String.valueOf(userId), sb.toString(), markup);
    }

    // 🔹 Побудова клавіатури з кнопками + Назад + Кошик
    private List<KeyboardRow> buildKeyboard(List<String> items, boolean withBottom) {
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow currentRow = new KeyboardRow();
        int count = 0;

        for (String item : items) {
            currentRow.add(item);
            count++;

            if (count == 3) { // максимум 3 кнопки в рядку
                keyboard.add(currentRow);
                currentRow = new KeyboardRow();
                count = 0;
            }
        }

        // якщо залишилися кнопки
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        // додати кнопку "Назад"
        if (withBottom) {
            KeyboardRow bottom = new KeyboardRow();
            bottom.add("⬅ Назад");
            keyboard.add(bottom);
        }

        return keyboard;
    }

    // 🔹 Очистити кошик
    private void clearCart(Long userId) throws TelegramApiException {
        userCart.remove(userId);
        sendText(String.valueOf(userId), "🧹 Кошик очищено!");
        sendMessage(createUserMenu(String.valueOf(userId), userId));
    }

    // 🔹 Назад
    private void handleBack(Long chatId) throws TelegramApiException {
        if (currentSubcategory.containsKey(chatId)) {
            currentSubcategory.remove(chatId);
            productIndex.remove(chatId);
            sendSubcategories(chatId, currentCategory.get(chatId));
        } else if (currentCategory.containsKey(chatId)) {
            currentCategory.remove(chatId);
            sendCategories(chatId);
        } else {
            sendCategories(chatId);
        }
    }

    // 🔹 Показ наступного товару
    private void showNextProduct(Long chatId) throws TelegramApiException {
        List<Map<String, Object>> products = catalogSearcher.getProducts(
                currentCategory.get(chatId), currentSubcategory.get(chatId)
        );

        if (products == null || products.isEmpty()) {
            sendText(chatId, "❌ У цій підкатегорії немає товарів.");
            return;
        }

        // Беремо індекс товару
        int index = productIndex.getOrDefault(chatId, 0);
        Map<String, Object> product = products.get(index);

        // Зберігаємо останній показаний товар для кнопки "Додати в кошик"
        lastShownProduct.put(chatId, product);

        String name = product.getOrDefault("name", "Без назви").toString();
        String price = product.getOrDefault("price", "N/A").toString();
        String unit = product.getOrDefault("unit", "шт").toString();
        String description = product.getOrDefault("description", "").toString();
        String photoPath = product.getOrDefault("photo", "").toString();
        String manufacturer = product.getOrDefault("manufacturer", "").toString();

        StringBuilder sb = new StringBuilder("📦 ").append(name)
                .append("\n💰 Ціна: ").append(price).append(" грн за ").append(unit);
        if (!manufacturer.isEmpty()) sb.append("\n🏭 Виробник: ").append(manufacturer);
        if (!description.isEmpty()) sb.append("\n📖 ").append(description);

        KeyboardRow row = new KeyboardRow();
        row.add("➡ Далі");
        row.add("🛒 Додати в кошик");

        List<KeyboardRow> kb = new ArrayList<>();
        kb.add(row);
        kb.add(new KeyboardRow(List.of(new KeyboardButton("⬅ Назад"))));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(kb);

        if (photoPath != null && !photoPath.isEmpty()) {
            String fileName = new java.io.File(photoPath).getName();
            sendPhotoFromResources(chatId.toString(), fileName, sb.toString(), markup);
        } else {
            sendText(chatId.toString(), sb.toString());
        }

        // Після показу товару збільшуємо індекс для наступного показу
        index++;
        if (index >= products.size()) index = 0;
        productIndex.put(chatId, index);
    }

    // 🔹 Додати в кошик
    private void addToCart(Long chatId) throws TelegramApiException {
        Map<String, Object> product = lastShownProduct.get(chatId);

        if (product == null) {
            sendText(chatId, "❌ Неможливо додати товар. Спробуйте ще раз.");
            return;
        }

        userCart.computeIfAbsent(chatId, k -> new ArrayList<>()).add(product);
        sendText(chatId, "✅ Товар \"" + product.get("name") + "\" додано до кошика!");
    }

    private final UserManager userManager = new UserManager();

    private void handleState(Long userId, String chatId, String text, String state, Update update) {

        switch (state) {

            case "search_catalog" -> handleSearch(userId, chatId, text);
            case "edit_product" -> handleEditProductStart(userId, chatId, text);
            case "choose_product" -> handleChooseProduct(userId, chatId, text);
            case "editing" -> handleEditing(userId, chatId, text);
            case "awaiting_field_value" -> handleAwaitingField(userId, chatId, text);
            case "awaiting_subcategory" -> handleAddToSubcategory(userId, chatId, text);
            case "add_hit" -> handleAddHit(userId, chatId, text);
            case "add_category" -> handleAddCategory(userId, chatId, text);
            case "add_subcategory" -> handleAddSubcategory(userId, chatId, text);
            case "add_new_subcategory" -> handleAddNewSubcategory(userId, chatId, text);
            case "choose_category_for_sub" -> handleChooseCategoryForSub(userId, chatId, text);
            case "delete_category_select" -> handleDeleteCategorySelect(userId, chatId, text);
            case "category_management" -> handleCategoryManagementState(userId, chatId, text);
            case "waiting_for_search" -> handleWaitingForSearch(userId, chatId, text);
            case "waiting_for_product_number" -> handleWaitingForProductNumber(userId, chatId, text);

            // ← Додаємо обробку стану відхилення замовлення
            case "reject_order_reason" -> {
                String reason = text; // текст, який ввів адміністратор

                try (Connection conn = DatabaseManager.getConnection()) {
                    // Беремо перше замовлення з бази
                    String sql = "SELECT * FROM orders WHERE status != 'Підтверджено' AND status != 'Відхилено' ORDER BY id ASC LIMIT 1";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);
                         ResultSet rs = stmt.executeQuery()) {

                        if (!rs.isBeforeFirst()) {
                            sendText(chatId, "Замовлень немає.");
                            userStates.remove(userId);
                            break;
                        }

                        if (rs.next()) {
                            Long orderUserId = rs.getLong("userId");
                            String orderCode = rs.getString("orderCode");

                            // Відправляємо користувачу повідомлення про відхилення
                            sendText(orderUserId.toString(), "❌ Ваше замовлення відхилено.\nПричина: " + reason);

                            // Оновлюємо статус і коментар у базі
                            String updateSql = "UPDATE orders SET status = ?, comment = ? WHERE orderCode = ?";
                            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                                updateStmt.setString(1, "Відхилено");
                                updateStmt.setString(2, reason);
                                updateStmt.setString(3, orderCode);
                                int rows = updateStmt.executeUpdate();
                                if (rows == 0) {
                                    sendText(chatId, "❌ Не вдалося оновити замовлення у базі.");
                                }
                            }

                            // Повідомляємо адміну
                            sendText(chatId, "Замовлення відхилено ✅");

                            // Показуємо наступне замовлення адміну
                            showAdminOrder(userId, chatId);
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendText(chatId, "❌ Помилка при обробці замовлення.");
                }

                // Очищаємо стан
                userStates.remove(userId);
            }

            case "reply_to_customer" -> {
                if (!ADMINS.contains(userId)) {
                    sendText(chatId, "⛔ У вас немає доступу.");
                    break;
                }

                Optional<Long> targetUserIdOpt = supportAnswers.keySet().stream().findFirst();
                if (targetUserIdOpt.isEmpty()) {
                    sendText(chatId, "❌ Немає користувачів для відповіді.");
                    break;
                }

                Long targetUserId = targetUserIdOpt.get();
                List<String> messages = supportAnswers.get(targetUserId);
                if (messages == null || messages.isEmpty()) {
                    sendText(chatId, "❌ Повідомлень від користувача немає.");
                    break;
                }

                String userMessage = messages.get(0); // перше повідомлення користувача

                // Встановлюємо стан для очікування відповіді адміністратора
                userStates.put(userId, "awaiting_admin_reply");
                tempStorage.put(userId + "_reply_to", targetUserId);
                tempStorage.put(userId + "_user_message", userMessage);

                sendText(chatId,
                        "✉ Повідомлення від користувача: " + targetUserId + "\n\n" +
                                userMessage + "\n\n✏️ Введіть вашу відповідь:"
                );
            }

            case "ask_consultant" -> {
                if (text != null) {
                    supportAnswers.computeIfAbsent(userId, k -> new ArrayList<>()).add(text);
                    userStates.remove(userId);
                    sendText(chatId, "✅ Ваше повідомлення надіслано консультанту!");
                }
            }

            // 📌 Введення ID користувача для відповіді
            case "waiting_for_feedback" -> {
                FeedbackManager.addFeedback(userId, text);
                sendText(chatId, "✅ Ваш відгук надіслано адміністратору!");
                userStates.remove(userId);
            }

            case "writing_reply" -> {
                Long replyTargetId = adminReplyTarget.get(userId); // Перейменовано, щоб уникнути конфлікту
                if (replyTargetId != null) {
                    sendText(replyTargetId.toString(), "📩 Відповідь від адміністратора:\n" + text);
                    sendText(chatId, "✅ Відповідь надіслана користувачу " + replyTargetId);
                } else {
                    sendText(chatId, "❌ Не знайдено користувача для відповіді.");
                }
                userStates.remove(userId);
                adminReplyTarget.remove(userId);
            }

            case "awaiting_admin_reply" -> {
                Long replyTargetId = (Long) tempStorage.get(userId + "_reply_to"); // Оголошення змінної
                if (replyTargetId != null) {
                    sendText(replyTargetId.toString(), "💬 Відповідь адміністратора:\n\n" + text);
                    sendText(chatId, "✅ Ваша відповідь надіслана користувачу " + replyTargetId);
                } else {
                    sendText(chatId, "❌ Не знайдено користувача для відповіді.");
                }

                // Очищаємо стан
                userStates.remove(userId);
                tempStorage.remove(userId + "_reply_to");
                tempStorage.remove(userId + "_user_message");
            }

            case "choose_delivery_type" -> {
                System.out.println("DEBUG: User ID = " + userId + ", State = " + userStates.get(userId) + ", Text = " + text);
                // Вибір способу доставки
                if ("🏬 Самовивіз".equals(text)) {
                    tempStorage.put(userId + "_deliveryType", "Самовивіз");
                    sendText(chatId, "📝 Введіть, будь ласка, дані для самовивозу у форматі:\n" +
                            "🏙 Місто, 👤 П.І., 📞 Телефон, 💳 Картка");
                    userStates.put(userId, "order_pickup");
                } else if ("📍 Доставка по місту".equals(text)) {
                    tempStorage.put(userId + "_deliveryType", "Доставка по місту");
                    sendText(chatId, "📝 Введіть, будь ласка, дані для доставки по місту у форматі:\n" +
                            "📍 Адреса, 👤 П.І., 📞 Телефон, 💳 Картка");
                    userStates.put(userId, "awaiting_city_delivery");
                } else if ("📮 Нова Пошта".equals(text)) {
                    tempStorage.put(userId + "_deliveryType", "Нова Пошта");
                    sendText(chatId, "📝 Введіть, будь ласка, дані для доставки Новою Поштою у форматі:\n" +
                            "📮 Відділення НП, 👤 П.І., 📞 Телефон, 💳 Картка");
                    userStates.put(userId, "awaiting_post_delivery");
                }
            }

            case "awaiting_hit_type" -> {
                if (!ADMINS.contains(userId)) break;
                if (text == null) return;

                if (text.equalsIgnoreCase("з описом")) {
                    userStates.put(userId, "awaiting_hit_title");
                    sendText(chatId, "Введіть назву товару для Хіт продажу:");
                } else if (text.equalsIgnoreCase("тільки медіа")) {
                    userStates.put(userId, "awaiting_hit_media_only");
                    sendText(chatId, "Відправте фото або відео (або напишіть 'немає'):");
                } else {
                    sendText(chatId, "Будь ласка, напишіть 'З описом' або 'Тільки медіа'");
                }
                return;
            }

            case "awaiting_hit_title" -> {
                if (text == null || text.isBlank()) {
                    sendText(chatId, "❌ Будь ласка, введіть назву товару.");
                    return;
                }
                tempStorage.put(userId + "_hit_title", text);
                userStates.put(userId, "awaiting_hit_description");
                sendText(chatId, "Введіть опис товару (або напишіть 'немає'):");
                return;
            }

            case "awaiting_hit_description" -> {
                if (text == null) {
                    sendText(chatId, "❌ Будь ласка, введіть опис товару.");
                    return;
                }
                tempStorage.put(userId + "_hit_description", text.equalsIgnoreCase("немає") ? "немає" : text);
                userStates.put(userId, "awaiting_hit_media");
                sendText(chatId, "Відправте фото або відео (або напишіть 'немає'):");
                return;
            }

            case "awaiting_hit_media" -> {
                String title = tempStorage.getOrDefault(userId + "_hit_title", "немає").toString();
                String description = tempStorage.getOrDefault(userId + "_hit_description", "немає").toString();
                String media = "немає";

                if (update.getMessage().hasPhoto()) {
                    List<PhotoSize> photos = update.getMessage().getPhoto();
                    media = photos.get(photos.size() - 1).getFileId();
                } else if (update.getMessage().hasVideo()) {
                    media = update.getMessage().getVideo().getFileId();
                } else if (text != null && text.equalsIgnoreCase("немає")) {
                    media = "немає";
                } else if (text != null && !text.isBlank()) {
                    media = text;
                }

                HitsManager.saveHit(title, description, media);

                // Очищення
                userStates.remove(userId);
                tempStorage.remove(userId + "_hit_title");
                tempStorage.remove(userId + "_hit_description");

                sendText(chatId, "✅ Товар успішно додано у Хіт продажу!");

                // Розсилка всім користувачам
                for (Long uid : userManager.getRegisteredUsers()) {
                    if (!ADMINS.contains(uid)) {
                        try {
                            sendText(uid, "🌟 Новий Хіт продажу з’явився в магазині!\nПерегляньте його у розділі «Хіти продажів»!");
                        } catch (Exception ignored) {}
                    }
                }
                return;
            }

            case "awaiting_hit_media_only" -> {
                String media = null;

                if (update.getMessage().hasPhoto()) {
                    List<PhotoSize> photos = update.getMessage().getPhoto();
                    media = photos.get(photos.size() - 1).getFileId();
                } else if (update.getMessage().hasVideo()) {
                    media = update.getMessage().getVideo().getFileId();
                } else if (text != null && text.equalsIgnoreCase("немає")) {
                    media = "немає";
                } else {
                    sendText(chatId, "❌ Будь ласка, надішліть фото або відео, або напишіть 'немає'.");
                    return;
                }

                HitsManager.saveHit(null, "немає", media); // title=null, description="немає"

                userStates.remove(userId);
                tempStorage.remove(userId + "_hit_media");

                sendText(chatId, "✅ Товар успішно додано у Хіт продажу!");

                for (Long uid : userManager.getRegisteredUsers()) {
                    if (!ADMINS.contains(uid)) {
                        try {
                            sendText(uid, "🌟 Новий Хіт продажу з’явився в магазині!\nПерегляньте його у розділі «Хіти продажів»!");
                        } catch (Exception ignored) {}
                    }
                }
                return;
            }

            // Обробка вибору доставки
            case "awaiting_delivery_choice" -> {
                switch (text) {
                    case "🏬 Самовивіз" -> {
                        tempStorage.put(userId + "_deliveryType", "Самовивіз");
                        userStates.put(userId, "order_pickup");
                        sendText(chatId,
                                "✏️ Введіть, будь-ласка, свої дані для самовивозу у форматі:\n" +
                                        "🏙 Місто\n👤 П.І.\n📞 Телефон\n💳 Номер картки (Магазину)\n\n" +
                                        "📌 Приклад:\n" +
                                        "Казанка, Сидоренко Олена Олексіївна, +380631234567, 4444");
                    }

                    case "📦 Доставка по місту" -> {
                        tempStorage.put(userId + "_deliveryType", "Доставка по місту");
                        userStates.put(userId, "awaiting_city_delivery");
                        sendText(chatId,
                                "📝 Введіть, будь-ласка, дані для доставки по місту у форматі:\n" +
                                        "📍 Адреса, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                        "📌 Приклад:\n" +
                                        "вул. Шевченка 10, Казанка, Петров Петро Петрович, +380671234567, 4444");
                    }

                    case "📮 Доставка Новою поштою" -> {
                        tempStorage.put(userId + "_deliveryType", "Нова пошта");
                        userStates.put(userId, "awaiting_post_delivery");
                        sendText(chatId,
                                "📝 Введіть, будь-ласка, дані для доставки Новою Поштою у форматі:\n" +
                                        "📮 Відділення НП, 👤 П.І., 📞 Телефон, 💳 Номер картки (Магазину)\n\n" +
                                        "📌 Приклад:\n" +
                                        "№12, Іваненко Іван Іванович, +380501234567, 4444");
                    }

                    case "⬅️ Назад" -> {
                        // Повернення у меню кошика
                        userStates.put(userId, "cart_menu");
                        try {
                            showCart(userId); // тут викликаємо твій метод для показу кошика
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                            sendText(chatId, "❌ Сталася помилка при показі кошика.");
                        }
                    }

                    default -> sendText(chatId, "❌ Будь ласка, оберіть один із варіантів кнопок нижче.");
                }
            }

            case "order_pickup" -> {
                List<Map<String, Object>> cart = userCart.get(userId);
                if (cart == null || cart.isEmpty()) {
                    sendText(chatId, "🛒 Ваш кошик порожній.");
                    userStates.remove(userId);
                    return;
                }

                String orderCode = String.format("%04d", new Random().nextInt(10000));
                String[] parts = text.split(",", 4); // Місто, П.І., Телефон, Картка
                String city = parts.length > 0 ? parts[0].trim() : "Невідомо";
                String fullName = parts.length > 1 ? parts[1].trim() : "Невідомо";
                String phone = parts.length > 2 ? parts[2].trim() : "Невідомо";
                String card = parts.length > 3 ? parts[3].trim() : "Немає";

                StringBuilder itemsDb = new StringBuilder();
                double total = 0;
                for (Map<String, Object> item : cart) {
                    String name = item.getOrDefault("name", "Без назви").toString();
                    double price = 0;
                    Object priceObj = item.get("price");
                    if (priceObj instanceof Number n) price = n.doubleValue();
                    else if (priceObj != null) {
                        try { price = Double.parseDouble(priceObj.toString()); } catch (NumberFormatException ignored) {}
                    }
                    itemsDb.append(name).append(":").append(price).append(";");
                    total += price;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "INSERT INTO orders (orderCode, userId, deliveryType, city, fullName, phone, card, status, item, total, date) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, orderCode);
                        stmt.setLong(2, userId);
                        stmt.setString(3, "Самовивіз");
                        stmt.setString(4, city);
                        stmt.setString(5, fullName);
                        stmt.setString(6, phone);
                        stmt.setString(7, card);
                        stmt.setString(8, "Нове");
                        stmt.setString(9, itemsDb.toString());
                        stmt.setDouble(10, total);
                        stmt.executeUpdate();
                    }

                    userCart.remove(userId);
                    userStates.remove(userId);

                    sendText(chatId, "✅ Ваше замовлення успішно оформлено!\nКод замовлення: " + orderCode +
                            "\nВаше замовлення:\n" + itemsDb.toString().replace(";", "\n") +
                            "\n💰 Всього: " + total + " грн\nБудь ласка, заберіть товар у магазині.");

                    // Надсилаємо адміну
                    for (Long adminId : ADMINS) {
                        showAdminOrder(adminId, adminId.toString());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendText(chatId, "❌ Сталася помилка при збереженні замовлення.");
                }
            }

            case "awaiting_city_delivery" -> {
                List<Map<String, Object>> cart = userCart.get(userId);
                if (cart == null || cart.isEmpty()) {
                    sendText(chatId, "🛒 Ваш кошик порожній.");
                    userStates.remove(userId);
                    return;
                }

                String orderCode = String.format("%04d", new Random().nextInt(10000));
                String[] parts = text.split(",", 4); // Адреса, П.І., Телефон, Картка
                String address = parts.length > 0 ? parts[0].trim() : "Невідомо";
                String fullName = parts.length > 1 ? parts[1].trim() : "Невідомо";
                String phone = parts.length > 2 ? parts[2].trim() : "Невідомо";
                String card = parts.length > 3 ? parts[3].trim() : "Немає";

                StringBuilder itemsDb = new StringBuilder();
                double total = 0;
                for (Map<String, Object> item : cart) {
                    String name = item.getOrDefault("name", "Без назви").toString();
                    double price = 0;
                    Object priceObj = item.get("price");
                    if (priceObj instanceof Number n) price = n.doubleValue();
                    else if (priceObj != null) {
                        try { price = Double.parseDouble(priceObj.toString()); } catch (NumberFormatException ignored) {}
                    }
                    itemsDb.append(name).append(":").append(price).append(";");
                    total += price;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "INSERT INTO orders (orderCode, userId, deliveryType, address, fullName, phone, card, status, item, total, date) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, orderCode);
                        stmt.setLong(2, userId);
                        stmt.setString(3, "Доставка по місту");
                        stmt.setString(4, address);
                        stmt.setString(5, fullName);
                        stmt.setString(6, phone);
                        stmt.setString(7, card);
                        stmt.setString(8, "Нове");
                        stmt.setString(9, itemsDb.toString());
                        stmt.setDouble(10, total);
                        stmt.executeUpdate();
                    }

                    userCart.remove(userId);
                    userStates.remove(userId);

                    sendText(chatId, "✅ Ваше замовлення успішно оформлено!\nКод замовлення: " + orderCode +
                            "\nВаше замовлення:\n" + itemsDb.toString().replace(";", "\n") +
                            "\n💰 Всього: " + total + " грн\nВаш товар буде доставлений за вказаною адресою.");

                    // Надсилаємо адміну
                    for (Long adminId : ADMINS) {
                        showAdminOrder(adminId, adminId.toString());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendText(chatId, "❌ Сталася помилка при збереженні замовлення.");
                }
            }

            case "awaiting_post_delivery" -> {
                List<Map<String, Object>> cart = userCart.get(userId);
                if (cart == null || cart.isEmpty()) {
                    sendText(chatId, "🛒 Ваш кошик порожній.");
                    userStates.remove(userId);
                    return;
                }

                String orderCode = String.format("%04d", new Random().nextInt(10000));
                String[] parts = text.split(",", 4); // Відділення НП, П.І., Телефон, Картка
                String postOffice = parts.length > 0 ? parts[0].trim() : "Невідомо";
                String fullName = parts.length > 1 ? parts[1].trim() : "Невідомо";
                String phone = parts.length > 2 ? parts[2].trim() : "Невідомо";
                String card = parts.length > 3 ? parts[3].trim() : "Немає";

                StringBuilder itemsDb = new StringBuilder();
                double total = 0;
                for (Map<String, Object> item : cart) {
                    String name = item.getOrDefault("name", "Без назви").toString();
                    double price = 0;
                    Object priceObj = item.get("price");
                    if (priceObj instanceof Number n) price = n.doubleValue();
                    else if (priceObj != null) {
                        try { price = Double.parseDouble(priceObj.toString()); } catch (NumberFormatException ignored) {}
                    }
                    itemsDb.append(name).append(":").append(price).append(";");
                    total += price;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "INSERT INTO orders (orderCode, userId, deliveryType, postOffice, fullName, phone, card, status, item, total, date) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, orderCode);
                        stmt.setLong(2, userId);
                        stmt.setString(3, "Нова пошта");
                        stmt.setString(4, postOffice);
                        stmt.setString(5, fullName);
                        stmt.setString(6, phone);
                        stmt.setString(7, card);
                        stmt.setString(8, "Нове");
                        stmt.setString(9, itemsDb.toString());
                        stmt.setDouble(10, total);
                        stmt.executeUpdate();
                    }

                    userCart.remove(userId);
                    userStates.remove(userId);
                    tempStorage.remove(userId + "_deliveryType");

                    sendText(chatId, "✅ Ваше замовлення успішно оформлено!\nКод замовлення: " + orderCode +
                            "\nВаше замовлення:\n" + itemsDb.toString().replace(";", "\n") +
                            "\n💰 Всього: " + total + " грн\nВаш товар буде доставлений Новою поштою за вказаним відділенням.");

                    // Надсилаємо адміну
                    for (Long adminId : ADMINS) {
                        showAdminOrder(adminId, adminId.toString());
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendText(chatId, "❌ Сталася помилка при збереженні замовлення.");
                }
            }

            case "invites_menu" -> {
                switch (text) {
                    case "➕ Додати запрошення" -> {
                        userStates.put(userId, "add_invite");
                        sendText(chatId, "✏️ Введіть дані нового запрошення у форматі:\nName;Kasa;City");
                    }
                    case "🗑️ Видалити запрошення" -> {
                        userStates.put(userId, "delete_invite");
                        sendText(chatId, "✏️ Введіть ID запрошення для видалення:");
                    }
                    case "✏️ Редагувати запрошення" -> {
                        userStates.put(userId, "edit_invite");
                        sendText(chatId, "✏️ Введіть дані для редагування у форматі:\nID;Name;Kasa;City");
                    }
                    case "📄 Показати всі запрошення" -> {
                        String sql = "SELECT * FROM invites ORDER BY id ASC";

                        try (Connection conn = DatabaseManager.getConnection();
                             PreparedStatement stmt = conn.prepareStatement(sql);
                             ResultSet rs = stmt.executeQuery()) {

                            StringBuilder sb = new StringBuilder("🔗 Статистика запрошень:\n\n");
                            boolean hasInvites = false;

                            while (rs.next()) {
                                hasInvites = true;
                                sb.append("🆔 ID: ").append(rs.getInt("id")).append("\n")
                                        .append("👤 Ім'я: ").append(rs.getString("name")).append("\n")
                                        .append("💰 Каса: ").append(rs.getString("kasa")).append("\n")
                                        .append("🏙️ Місто: ").append(rs.getString("city")).append("\n")
                                        .append("📈 Кількість приєднались: ").append(rs.getInt("number")).append("\n")
                                        .append("-----------------------------\n");
                            }

                            if (!hasInvites) {
                                sendText(chatId, "Поки що немає запрошень.");
                            } else {
                                sendText(chatId, sb.toString());
                            }

                        } catch (SQLException e) {
                            e.printStackTrace();
                            sendText(chatId, "❌ Сталася помилка при отриманні запрошень.");
                        }
                    }
                    case "⬅️ Назад в розробника" -> {
                        sendMessage(createDeveloperMenu(chatId));
                        userStates.remove(userId);
                    }
                    default -> sendText(chatId, "❌ Некоректна команда.");
                }
            }

            // --- Додати нове запрошення
            case "add_invite" -> {
                String[] parts = text.split(";");
                if (parts.length < 3) {
                    sendText(chatId, "❌ Некоректний формат! Використовуйте Name;Kasa;City");
                } else {
                    boolean success = InviteManager.addInvite(parts[0], parts[1], parts[2], botUsername);
                    if (success) sendText(chatId, "✅ Запрошення додано!");
                    else sendText(chatId, "❌ Сталася помилка при додаванні запрошення.");
                }
                userStates.remove(userId);
            }

            // --- Видалити запрошення
            case "delete_invite" -> {
                try {
                    int id = Integer.parseInt(text.trim());
                    boolean deleted = InviteManager.deleteInvite(id); // потрібно додати метод у InviteManager
                    if (deleted) sendText(chatId, "✅ Запрошення видалено!");
                    else sendText(chatId, "❌ Запрошення не знайдено.");
                } catch (Exception e) {
                    sendText(chatId, "❌ Некоректний ID!");
                }
                userStates.remove(userId);
            }

            // --- Редагувати запрошення
            case "edit_invite" -> {
                String[] parts = text.split(";");
                if (parts.length < 4) {
                    sendText(chatId, "❌ Некоректний формат! Використовуйте ID;Name;Kasa;City");
                } else {
                    try {
                        int id = Integer.parseInt(parts[0]);
                        boolean edited = InviteManager.editInvite(id, parts[1], parts[2], parts[3]); // потрібно додати метод у InviteManager
                        if (edited) sendText(chatId, "✅ Запрошення відредаговано!");
                        else sendText(chatId, "❌ Запрошення не знайдено!");
                    } catch (Exception e) {
                        sendText(chatId, "❌ Некоректний ID!");
                    }
                }
                userStates.remove(userId);
            }


            case "logs_invites" -> {
                Map<Integer, Map<String, Object>> invites = DeveloperFileManager.getAllInvites();
                if (invites.isEmpty()) {
                    sendText(chatId, "📊 Поки що немає запрошень.");
                } else {
                    StringBuilder sb = new StringBuilder("📊 Статистика запрошувальних посилань:\n\n");
                    for (Map.Entry<Integer, Map<String, Object>> entry : invites.entrySet()) {
                        Map<String, Object> data = entry.getValue();
                        sb.append("🆔 ID: ").append(entry.getKey()).append("\n")
                                .append("👤 Ім'я: ").append(data.get("name")).append("\n")
                                .append("💰 Каса: ").append(data.get("kasa")).append("\n")
                                .append("🏙️ Місто: ").append(data.get("city")).append("\n")
                                .append("📈 Кількість: ").append(data.get("number")).append("\n")
                                .append("-----------------------------\n");
                    }
                    sendText(chatId, sb.toString());
                }
                userStates.remove(userId);
            }

            case "logs_no_invite" -> {
                List<Long> noInviteUsers = DeveloperFileManager.getNoInviteUsers();
                int count = noInviteUsers.size(); // кількість користувачів без запрошень
                sendText(chatId, "📊 Кількість користувачів, які приєдналися без запрошень: " + count);
                userStates.remove(userId);
            }

            case "logs_orders" -> {
                Map<String, Integer> summary = DeveloperFileManager.getOrdersSummary();
                List<Map<String, String>> rejectedOrders = DeveloperFileManager.getRejectedOrders();

                StringBuilder message = new StringBuilder();
                message.append("📦 Статистика замовлень:\n")
                        .append("Всього замовлень: ").append(summary.getOrDefault("total", 0)).append("\n")
                        .append("Відправлено/готові: ").append(summary.getOrDefault("sent", 0)).append("\n")
                        .append("Відхилено: ").append(summary.getOrDefault("rejected", 0));

                if (!rejectedOrders.isEmpty()) {
                    message.append("\n\nПричини відхилення:");
                    for (Map<String, String> order : rejectedOrders) {
                        message.append("\n• [")
                                .append(order.get("orderCode"))
                                .append("] ")
                                .append(order.get("comment"));
                    }
                }

                sendText(chatId, message.toString());
                userStates.remove(userId);
            }

            case "editing_field_value" -> {
                String field = adminEditingField.get(userId);        // яке поле редагується
                String productName = adminEditingProduct.get(userId);

                if (productName == null || field == null) {
                    sendText(chatId, "❌ Сталася помилка. Спробуйте ще раз.");
                    userStates.remove(userId);
                    return;
                }

                String newValue = text.trim();

                // --- Перевірка для одиниці виміру ---
                if ("unit".equals(field)) {
                    if (!newValue.equalsIgnoreCase("шт") && !newValue.equalsIgnoreCase("метр")) {
                        sendText(chatId, "❌ Допустимі значення: 'шт' або 'метр'. Спробуйте ще раз:");
                        return; // залишаємо стан await
                    }
                }

                try {
                    CatalogEditor.updateField(productName, field, newValue);
                    sendText(chatId, "✅ Поле '" + field + "' успішно оновлено для товару '" + productName + "'");
                } catch (Exception e) {
                    sendText(chatId, "❌ Сталася помилка при оновленні поля '" + field + "'");
                    e.printStackTrace();
                }

                // --- Очищення станів ---
                userStates.remove(userId);
                adminEditingField.remove(userId);
                adminEditingProduct.remove(userId);
            }

            case "changelog_menu" -> {
                List<String> logs = DeveloperFileManager.getChangelog();
                if (logs.isEmpty()) sendText(chatId, "📝 Список онови поки що пустий.");
                else sendText(chatId, "📝 Changelog:\n" + String.join("\n", logs));
                userStates.remove(userId);
            }
        }
    }

    // 🔍 Пошук товару
    private void handleSearch(Long userId, String chatId, String text) {
        List<Map<String, Object>> products = loadCatalogFlat();
        List<Map<String, Object>> matches = new ArrayList<>();

        if (products == null || products.isEmpty()) {
            sendText(chatId, "❌ Каталог порожній або не завантажився.");
            userStates.remove(userId);
            return;
        }

        for (Map<String, Object> p : products) {
            String name = String.valueOf(p.get("name")).toLowerCase();
            if (name.contains(text.toLowerCase())) {
                matches.add(p);
            }
        }

        if (matches.isEmpty()) {
            sendText(chatId, "❌ Товар не знайдено. Спробуйте інший запит.");
        } else {
            searchResults.put(Long.parseLong(chatId), matches);
            productIndex.put(Long.parseLong(chatId), 0);
            try {
                sendSearchedProduct(Long.parseLong(chatId));
            } catch (TelegramApiException e) {
                e.printStackTrace();
                sendText(chatId, "⚠️ Помилка під час відображення товару.");
            }
        }

        userStates.remove(userId);
    }

    private void handleWaitingForProductNumber(Long userId, String chatId, String text) {
        List<Map<String, Object>> found = searchResults.get(Long.parseLong(chatId));
        if (found == null || found.isEmpty()) {
            sendText(chatId, "❌ Список товарів порожній. Спробуйте пошук ще раз.");
            userStates.remove(userId);
            return;
        }

        try {
            int number = Integer.parseInt(text.trim());
            if (number < 1 || number > found.size()) {
                sendText(chatId, "⚠️ Вкажіть номер від 1 до " + found.size());
                return;
            }

            Map<String, Object> chosenProduct = found.get(number - 1);
            searchResults.put(Long.parseLong(chatId), List.of(chosenProduct));
            productIndex.put(Long.parseLong(chatId), 0);
            sendSearchedProduct(Long.parseLong(chatId));
            userStates.remove(userId);

        } catch (NumberFormatException e) {
            sendText(chatId, "⚠️ Введіть лише номер товару.");
        } catch (TelegramApiException e) {
            sendText(chatId, "⚠️ Помилка під час показу товару.");
        }
    }

    private void handleWaitingForSearch(Long userId, String chatId, String text) {
        String query = text.trim();
        if (query.isEmpty()) {
            sendText(chatId, "⚠️ Введіть назву товару для пошуку.");
            return;
        }

        try {
            CatalogSearcher searcher = new CatalogSearcher();
            List<Map<String, Object>> foundProducts = new ArrayList<>();

            // Пошук у плоскому списку products
            for (Map<String, Object> p : searcher.getFlatProducts()) {
                String productName = String.valueOf(p.getOrDefault("name", "")).toLowerCase();
                if (productName.contains(query.toLowerCase())) {
                    foundProducts.add(new HashMap<>(p));
                }
            }

            // Пошук рекурсивно у catalog
            List<Map<String, Object>> catalog = searcher.getCatalog();
            if (catalog != null) {
                searcher.extractProductsFromCatalogForSearch(catalog, foundProducts, query);
            }

            System.out.println("🔎 Total found products: " + foundProducts.size());

            if (foundProducts.isEmpty()) {
                sendText(chatId, "❌ Товар не знайдено. Спробуйте інший запит.");
                userStates.remove(userId);
                return;
            }

            // Кілька результатів
            if (foundProducts.size() > 1) {
                StringBuilder sb = new StringBuilder("🔎 Знайдено кілька товарів. Введіть номер:\n\n");
                int index = 1;
                for (Map<String, Object> p : foundProducts) {
                    sb.append(index++).append(". ").append(p.get("name")).append("\n");
                }
                searchResults.put(Long.parseLong(chatId), foundProducts);
                userStates.put(userId, "waiting_for_product_number");
                sendText(chatId, sb.toString());
                return;
            }

            // Один результат
            searchResults.put(Long.parseLong(chatId), foundProducts);
            productIndex.put(Long.parseLong(chatId), 0);
            sendSearchedProduct(Long.parseLong(chatId));
            userStates.remove(userId);

        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "⚠️ Помилка під час пошуку товару.");
            userStates.remove(userId);
        }
    }


    // ✏️ Початок редагування товару
    private void handleEditProductStart(Long userId, String chatId, String text) {
        List<Map<String, Object>> matchesMap = catalogSearcher.searchByKeywordsAdmin(text);
        List<String> matches = new ArrayList<>();
        for (Map<String, Object> p : matchesMap) {
            matches.add((String) p.get("name"));
        }

        if (matches.isEmpty()) sendText(chatId, "❌ Товар не знайдено: " + text);
        else if (matches.size() == 1) {
            adminEditingProduct.put(userId, matches.get(0));
            userStates.put(userId, "editing");
            sendMessage(createEditMenu(chatId, matches.get(0)));
        } else {
            adminMatchList.put(userId, matches);
            userStates.put(userId, "choose_product");
            StringBuilder sb = new StringBuilder("Знайдено кілька товарів. Введіть номер:\n\n");
            for (int i = 0; i < matches.size(); i++) sb.append(i + 1).append(". ").append(matches.get(i)).append("\n");
            sendText(chatId, sb.toString());
        }
    }

    // Вибір товару по списку
    private void handleChooseProduct(Long userId, String chatId, String text) {
        List<String> matches = adminMatchList.get(userId);
        if (matches == null || matches.isEmpty()) {
            sendText(chatId, "❌ Помилка: список товарів порожній.");
            userStates.remove(userId);
            return;
        }

        try {
            int index = Integer.parseInt(text.trim()) - 1;
            if (index < 0 || index >= matches.size()) {
                sendText(chatId, "❌ Некоректний номер. Спробуйте ще раз.");
                return;
            }

            String selectedProduct = matches.get(index);
            adminEditingProduct.put(userId, selectedProduct);
            userStates.put(userId, "editing");
            adminMatchList.remove(userId);

            sendMessage(createEditMenu(chatId, selectedProduct));
        } catch (NumberFormatException e) {
            sendText(chatId, "❌ Будь ласка, введіть номер із списку.");
        }
    }

    // 🔧 Редагування товару
    private void handleEditing(Long userId, String chatId, String text) {
        String productName = adminEditingProduct.get(userId);
        if (productName == null) return;

        switch (text) {
            case "✏️ Назву":
                adminEditingField.put(userId, "name");
                userStates.put(userId, "awaiting_field_value");
                sendText(chatId, "Введіть нову назву товару:");
                break;

            case "💰 Ціну":
                adminEditingField.put(userId, "price");
                userStates.put(userId, "awaiting_field_value");
                sendText(chatId, "Введіть нову ціну:");
                break;

            case "📖 Опис":
                adminEditingField.put(userId, "description");
                userStates.put(userId, "awaiting_field_value");
                sendText(chatId, "Введіть новий опис:");
                break;

            case "🗂️ Додати в підкатегорію":
                userStates.put(userId, "awaiting_subcategory");
                sendText(chatId, "✏️ Введіть назву підкатегорії, куди хочете додати товар:");
                break;

            case "🖼️ Додати фотографію":
                userStates.put(userId, "awaiting_photo");
                sendText(chatId, "📷 Надішліть фото для товару '" + productName + "' зі свого комп’ютера:");
                break;

            case "📏 Одиниця виміру":
                adminEditingField.put(userId, "unit");
                userStates.put(userId, "editing_field_value");
                sendText(chatId, "Введіть одиницю виміру для товару (шт або метр):");
                break;

            case "🏭 Виробник":
                adminEditingField.put(userId, "manufacturer");
                userStates.put(userId, "editing_field_value");
                sendText(chatId, "✏️ Введіть назву виробника для товару (або ❌ щоб видалити):");
                break;

            default:
                sendText(chatId, "Невідома опція редагування.");
                break;
        }
    }

    // 📝 Очікування значення для редагування
    private void handleAwaitingField(Long userId, String chatId, String newValue) {
        String productName = adminEditingProduct.get(userId);
        String field = adminEditingField.get(userId);

        if (field == null || productName == null) return;

        CatalogEditor.updateField(productName, field, newValue);

        sendText(chatId, "✅ Поле '" + field + "' оновлено для товару: " + productName);
        adminEditingProduct.remove(userId);
        adminEditingField.remove(userId);
        userStates.remove(userId);
    }

    // ⭐ Додавання хіта продажу
    private void handleAddHit(Long userId, String chatId, String text) {
        hitItems.add("⭐ " + text);
        userStates.remove(userId);
        sendText(chatId, "Товар додано до хітів продажу!");
    }

    private void handleAddCategory(Long userId, String chatId, String text) {
        adminNewCategory.put(userId, text); // зберігаємо назву нової категорії
        userStates.put(userId, "add_subcategory");
        sendText(chatId, "✏️ Введіть назву підкатегорії для категорії '" + text + "' (можна пропустити, залишивши пустим):");
    }

    private void handleAddSubcategory(Long userId, String chatId, String subcategoryName) {
        String categoryName = adminNewCategory.get(userId);
        if (categoryName == null) {
            sendText(chatId, "❌ Сталася помилка. Спробуйте ще раз.");
            userStates.remove(userId);
            return;
        }

        // Додаємо категорію у CatalogEditor
        boolean catAdded = CatalogEditor.addCategory(categoryName);
        if (!catAdded) {
            sendText(chatId, "⚠️ Категорія вже існує: " + categoryName);
        }

        // Додаємо підкатегорію, якщо назва підкатегорії не порожня
        if (subcategoryName != null && !subcategoryName.isEmpty()) {
            boolean subAdded = CatalogEditor.addSubcategory(categoryName, subcategoryName);
            if (!subAdded) {
                sendText(chatId, "⚠️ Підкатегорія вже існує: " + subcategoryName);
            }
        }

        sendText(chatId, "✅ Категорія та підкатегорія додані у каталог:\nКатегорія: " + categoryName +
                (subcategoryName.isEmpty() ? "" : "\nПідкатегорія: " + subcategoryName));

        adminNewCategory.remove(userId);
        userStates.remove(userId);
    }

    private void handleAddToSubcategory(Long userId, String chatId, String subcategoryName) {
        String productName = adminEditingProduct.get(userId);
        if (productName == null) {
            sendText(chatId, "❌ Error: No product selected to add to the subcategory.");
            userStates.remove(userId);
            return;
        }

        System.out.println("INFO: Adding product '" + productName + "' to subcategory '" + subcategoryName + "'");

        // --- Get price from YAML
        double price = CatalogEditor.getProductPriceFromYAML(productName);
        if (price <= 0.0) {
            System.out.println("DEBUG: Price <= 0, setting default 0.0");
            price = 0.0;
        }

        // --- Check subcategory
        if (!CatalogEditor.subcategoryExists(subcategoryName)) {
            sendText(chatId, "❌ Subcategory '" + subcategoryName + "' not found in MySQL database.");
            userStates.remove(userId);
            return;
        }

        // --- Add product
        boolean success = CatalogEditor.addProductToSubcategory(productName, price, subcategoryName);

        if (success) {
            sendText(chatId, "✅ Product '" + productName + "' added to subcategory '" + subcategoryName + "'!");
        } else {
            sendText(chatId, "❌ Failed to add product '" + productName +
                    "' to subcategory '" + subcategoryName + "'. It might already exist.");
        }

        userStates.remove(userId);
    }

    private void handleChooseCategoryForSub(Long userId, String chatId, String categoryName) {
        // Перевіряємо, чи існує така категорія
        if (!CatalogEditor.categoryExists(categoryName)) {
            sendText(chatId, "❌ Категорію '" + categoryName + "' не знайдено. Перевірте назву.");
            return;
        }

        // Зберігаємо вибір і просимо ввести нову підкатегорію
        adminNewCategory.put(userId, categoryName);
        userStates.put(userId, "add_new_subcategory");
        sendText(chatId, "✏️ Введіть назву нової підкатегорії для категорії '" + categoryName + "':");
    }

    private void handleAddNewSubcategory(Long userId, String chatId, String subcategoryName) {
        String categoryName = adminNewCategory.get(userId);
        if (categoryName == null || subcategoryName.isEmpty()) {
            sendText(chatId, "❌ Сталася помилка. Спробуйте ще раз.");
            userStates.remove(userId);
            return;
        }

        boolean added = CatalogEditor.addSubcategory(categoryName, subcategoryName);

        if (added) {
            sendText(chatId, "✅ Підкатегорію '" + subcategoryName + "' додано до категорії '" + categoryName + "'.");
        } else {
            sendText(chatId, "❌ Не вдалося додати підкатегорію '" + subcategoryName + "'. Можливо, вона вже існує.");
        }

        adminNewCategory.remove(userId);
        userStates.remove(userId);
    }

    private void handleCategoryManagementState(Long userId, String chatId, String text) {
        switch (text) {
            case "➕ Додати категорію" -> {
                userStates.put(userId, "add_category"); // тут запускається твій handleAddCategory
                sendText(chatId, "✏️ Введіть назву нової категорії:");
            }
            case "➕ Додати підкатегорію" -> {
                userStates.put(userId, "choose_category_for_sub");
                sendText(chatId, "📂 Введіть назву категорії, до якої хочете додати нову підкатегорію:");
            }
            case "✏️ Змінити назву категорії" -> {
                userStates.put(userId, "rename_category_select");
                sendText(chatId, "✏️ Введіть назву категорії, яку хочете змінити:");
            }
            case "🗑️ Видалити категорію" -> {
                userStates.put(userId, "delete_category_select");
                sendText(chatId, "🗑️ Введіть назву категорії, яку хочете видалити:");
            }
            case "⬅️ Назад" -> {
                userStates.remove(userId);
                sendMessage(createAdminMenu(chatId));
            }
            default -> sendText(chatId, "🤖 Не зрозумів команду. Спробуйте ще раз.");
        }
    }

    private void handleDeveloperText(Long userId, String chatId, String text, String botUsername) {
        switch (text) {
            case "🔗 Запрошувальні посилання" -> sendMessage(createInvitesMenu(chatId));
            case "📜 Логирування" -> sendText(chatId, "📜 Тут буде логування..."); // пізніше можна додати статистику
            case "📝 Список онови" -> {
                List<String> changelog = DeveloperFileManager.getChangelog(); // List<String>
                if (changelog.isEmpty()) {
                    sendText(chatId, "📝 Поки що немає оновлень.");
                } else {
                    StringBuilder sb = new StringBuilder("📝 Список онови:\n");
                    for (String entry : changelog) {
                        sb.append("• ").append(entry).append("\n");
                    }
                    sendText(chatId, sb.toString());
                }
            }
            case "⬅️ Назад в головне меню" -> sendMessage(createUserMenu(chatId, userId));
        }
    }

    private void handleInvitesText(Long userId, String chatId, String text, String botUsername) {
        switch (text) {
            case "➕ Додати запрошення" -> {
                // Можна тут попросити ввести Name, Kasa, City через userStates
                userStates.put(userId, "awaiting_new_invite");
                sendText(chatId, "✏️ Введіть дані нового запрошення у форматі:\nName,Kasa,City");
            }
            case "✏️ Редагувати запрошення" -> {
                userStates.put(userId, "awaiting_edit_invite");
                sendText(chatId, "✏️ Введіть ID запрошення, яке хочете редагувати:");
            }
            case "🗑️ Видалити запрошення" -> {
                userStates.put(userId, "awaiting_delete_invite");
                sendText(chatId, "✏️ Введіть ID запрошення, яке хочете видалити:");
            }
            case "📄 Показати всі запрошення" -> {
                String sql = "SELECT * FROM invites ORDER BY id ASC";

                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    StringBuilder sb = new StringBuilder("🔗 Список запрошень:\n");
                    boolean hasInvites = false;

                    while (rs.next()) {
                        hasInvites = true;
                        sb.append("ID: ").append(rs.getInt("id"))
                                .append(", Name: ").append(rs.getString("name"))
                                .append(", Kasa: ").append(rs.getString("kasa"))
                                .append(", City: ").append(rs.getString("city"))
                                .append(", Invite: ").append(rs.getString("invite"))
                                .append(", Number: ").append(rs.getInt("number"))
                                .append("\n");
                    }

                    if (!hasInvites) {
                        sendText(chatId, "Поки що немає запрошень.");
                    } else {
                        sendText(chatId, sb.toString());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    sendText(chatId, "❌ Сталася помилка при отриманні запрошень.");
                }
            }
            case "⬅️ Назад в розробника" -> sendMessage(createDeveloperMenu(chatId));
        }
    }

    private void handleDeleteCategorySelect(Long userId, String chatId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            sendText(chatId, "❌ Помилка: назва категорії не може бути порожньою.");
            userStates.remove(userId);
            return;
        }

        boolean removed = CatalogEditor.deleteCategory(categoryName);
        if (removed) {
            sendText(chatId, "✅ Категорія '" + categoryName + "' успішно видалена!");
        } else {
            sendText(chatId, "❌ Категорія '" + categoryName + "' не знайдена. Перевірте назву.");
        }

        userStates.remove(userId);
    }

    private void handleAwaitingPhoto(Long userId, String chatId, List<PhotoSize> photos) {
        try {
            if (photos == null || photos.isEmpty()) {
                sendText(chatId, "❌ Фото не отримано. Спробуйте ще раз.");
                System.out.println("[PHOTO] Список фото порожній для userId=" + userId);
                return;
            }

            String productName = adminEditingProduct.get(userId);
            if (productName == null) {
                sendText(chatId, "❌ Назву товару не знайдено. Почніть редагування заново.");
                System.out.println("[PHOTO] Товар для редагування не знайдено для userId=" + userId);
                return;
            }

            // Беремо останнє фото (найбільше за розміром)
            PhotoSize photo = photos.get(photos.size() - 1);
            String fileId = photo.getFileId();
            System.out.println("[PHOTO] Отримано fileId: " + fileId);

            org.telegram.telegrambots.meta.api.methods.GetFile getFileMethod = new GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);

            // Створюємо каталог src/main/resources/images, якщо його нема
            java.io.File dir = new java.io.File("src/main/resources/images");
            if (!dir.exists()) dir.mkdirs();

            // Зберігаємо файл з fileId.jpg у resources/images
            String filePath = "src/main/resources/images/" + fileId + ".jpg";
            java.io.File localFile = new java.io.File(filePath);

            try (InputStream is = new URL(file.getFileUrl(getBotToken())).openStream();
                 FileOutputStream fos = new FileOutputStream(localFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            System.out.println("[PHOTO] Фото успішно збережено: " + localFile.getAbsolutePath());

            // Оновлюємо поле photo у YAML з відносним шляхом для JAR
            String relativePath = "images/" + fileId + ".jpg";
            CatalogEditor.updateField(productName, "photo", relativePath);

            sendText(chatId, "✅ Фото успішно додано до товару '" + productName + "'.");

            // Очищуємо стан користувача
            userStates.remove(userId);
            adminEditingProduct.remove(userId);

        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "❌ Сталася помилка при завантаженні фото.");
        }
    }

    // Завантаження каталогу у плоский список
    private List<Map<String, Object>> loadCatalogFlat() {
        try {
            CatalogSearcher cs = new CatalogSearcher();
            List<Map<String, Object>> allProducts = new ArrayList<>();

            // Беремо всі категорії
            for (String cat : cs.getCategories()) {
                // Беремо всі підкатегорії
                for (String sub : cs.getSubcategories(cat)) {
                    // Додаємо товари в список
                    allProducts.addAll(cs.getProducts(cat, sub));
                }
            }

            return allProducts;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private SendMessage createUserMenu(String chatId, Long userId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🧱 Каталог товарів");
        row1.add("🔍 Пошук товару");
        row1.add("📋 Кошик");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🎯 Хіт продажу");
        row2.add("📍 Адреси та Контакти");
        row2.add("\uD83C\uDF10 Соц-мережі");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("💬 Залишити відгук");
        row3.add("💬 Допомога");
        keyboard.add(row3);

        // 🔹 Якщо адмін — додаємо кнопку адмін-панелі
        if (ADMINS.contains(userId)) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add("⚙️ Продавца меню");
            keyboard.add(adminRow);
        }

        // Якщо розробник
        if (DEVELOPERS.contains(userId)) {
            KeyboardRow devRow = new KeyboardRow();
            devRow.add("👨‍💻 Меню розробника");
            keyboard.add(devRow);
        }

        markup.setKeyboard(keyboard);
        return SendMessage.builder()
                .chatId(chatId)
                .text("👋 Привіт, друже!\n" +
                        "Мене звати Митрофан 🤖 — я твій вірний помічник у цьому чудовому телеграм-магазині 🛍️\n\n" +
                        "Кажуть, я вмію знаходити все 😉 — від потрібного товару до вигідної знижки 💸\n" +
                        "Тож розслабся, бери каву ☕ і дозволь мені допомогти зробити твої покупки простими та приємними 💫\n\n" +
                        "✨ У нашому магазині ти знайдеш усе, що потрібно, а я допоможу розібратися крок за кроком:\n\n" +
                        "🔹 Каталог товарів — переглядай категорії й підкатегорії, знаходь потрібні товари на замовлення або просто напиши мені, і я допоможу 😉\n\n" +
                        "🔎 Пошук товару — введи назву або частину слова, і я миттєво покажу потрібний результат 💡\n\n" +
                        "🧺 Кошик і доставка — додавай товари до кошика й обирай зручний спосіб отримання:\n" +
                        "🚚 Нова пошта | 🏠 Доставка додому | 🏬 Самовивіз із наших магазинів.\n\n" +
                        "⭐ Відгуки — мені дуже приємно читати ваші слова ❤️ Кожен відгук допомагає мені ставати кращим 💪\n\n" +
                        "🔥 Хіти продажів та знижки — не пропусти акції, сезонні пропозиції та найпопулярніші товари 🌞❄️\n\n" +
                        "💡 Допомога — маєш питання? Запитуй мене або зв’яжись із нашими консультантами 🧡\n\n" +
                        "🌐 Соцмережі та адреси магазинів — дізнавайся про новинки та завітай особисто 🏪\n\n" +
                        "🫶 Я радий, що ти тут!\n" +
                        "Разом ми зробимо твої покупки легкими, комфортними й трішки чарівними 🌈\n\n" +
                        "З повагою, твій вірний помічник — Митрофан 🤖💙")
                .replyMarkup(markup)
                .build();
    }

    private SendMessage createAdminMenu(String chatId) {
        SendMessage msg = new SendMessage(chatId, "🔐 Адмін-панель:");
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);

        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("🛒 Замовлення користувачів"));
        r1.add(new KeyboardButton("💬 Відповісти покупцю")); // <-- нова кнопка

        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("⬅️ Назад"));
        kb.setKeyboard(List.of(r1, r2));

        msg.setReplyMarkup(kb);
        return msg;
    }

    private SendMessage createDeveloperMenu(String chatId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🔄 Оновити каталог");
        row1.add("✏️ Редагувати товар");
        row1.add("Редагувати категорії");
        row1.add("⭐ Додати товар у Хіт продажу");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🔗 Запрошувальні посилання");
        row2.add("📜 Логирування");
        row2.add("📝 Список онови");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("💬 Відгуки користувачів");
        row3.add("⬅️ Назад в головне меню");
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return SendMessage.builder()
                .chatId(chatId)
                .text("👨‍💻 Меню розробника, оберіть дію:")
                .replyMarkup(markup)
                .build();
    }

    private SendMessage createEditMenu(String chatId, String productName) {
        SendMessage msg = new SendMessage(chatId, "Редагування товару: " + productName);
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("✏️ Назву"));
        r1.add(new KeyboardButton("💰 Ціну"));
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("📖 Опис"));
        r2.add(new KeyboardButton("🗂️ Додати в підкатегорію"));
        KeyboardRow r3 = new KeyboardRow();
        r3.add(new KeyboardButton("🖼️ Додати фотографію"));
        r3.add(new KeyboardButton("📏 Одиниця виміру"));
        KeyboardRow r4 = new KeyboardRow();
        r4.add(new KeyboardButton("🏭 Виробник"));
        r4.add(new KeyboardButton("⬅️ Назад"));
        kb.setKeyboard(List.of(r1, r2, r3, r4));
        msg.setReplyMarkup(kb);
        return msg;
    }

    private SendMessage createCategoryAdminMenu(String chatId) {
        SendMessage msg = new SendMessage(chatId, "🔧 Редагування категорій:");
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);

        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("➕ Додати категорію"));// стартує стан add_category
        r1.add(new KeyboardButton("➕ Додати підкатегорію"));
        r1.add(new KeyboardButton("✏️ Змінити назву категорії"));

        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("🗑️ Видалити категорію"));
        r2.add(new KeyboardButton("⬅️ Назад"));

        kb.setKeyboard(List.of(r1, r2));
        msg.setReplyMarkup(kb);
        return msg;
    }

    private SendMessage createHelpMenu(String chatId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add("✉️ Написати консультанту");
        row1.add("💌 Відповіді");
        keyboard.add(row1);
        KeyboardRow row3 = new KeyboardRow();
        row3.add("⬅ Назад");
        keyboard.add(row3);
        markup.setKeyboard(keyboard);
        return SendMessage.builder()
                .chatId(chatId)
                .text("📖 Виберіть один із пунктів для отримання допомоги:\n\n" +
                        "✉️ *Написати консультанту* – Задайте своє питання і отримайте професійну консультацію.\n" +
                        "💌 *Відповіді* – Перегляньте всі відповіді консультантів.")
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
    }

    private SendMessage createInvitesMenu(String chatId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("➕ Додати запрошення");
        row1.add("✏️ Редагувати запрошення");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🗑️ Видалити запрошення");
        row2.add("📄 Показати всі запрошення");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("⬅️ Назад в розробника");
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return SendMessage.builder()
                .chatId(chatId)
                .text("🔗 Меню запрошувальних посилань:")
                .replyMarkup(markup)
                .build();
    }

    private SendMessage createLogsMenu(String chatId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📊 Статистика запрошувань");
        row1.add("📊 Статистика без запрошень");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📦 Замовлення");
        row2.add("🔎 Перегляд повідомленней від покупців");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("⬅️ Назад в розробника");
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return SendMessage.builder()
                .chatId(chatId)
                .text("📜 Меню логування:")
                .replyMarkup(markup)
                .build();
    }

    private SendMessage createFeedbackMenu(String chatId, String userId, String feedbackText) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📩 Відповісти на відгук");
        row1.add("💾 Зберегти відгук");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🗑️ Видалити відгук");
        row2.add("⬅️ Назад у меню");
        keyboard.add(row2);

        markup.setKeyboard(keyboard);

        return SendMessage.builder()
                .chatId(chatId)
                .text("Відгук користувача " + userId + ":\n\n" + feedbackText + "\n\nОберіть дію:")
                .replyMarkup(markup)
                .build();
    }

    private void sendText(String chatId, String text) {
        int maxLength = 4000;
        try {
            for (int start = 0; start < text.length(); start += maxLength) {
                int end = Math.min(start + maxLength, text.length());
                SendMessage msg = new SendMessage(chatId, text.substring(start, end));
                msg.setParseMode("HTML"); // ✅ Вмикаємо HTML форматування
                execute(msg);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\u00A0\\s]+", " ").trim().toLowerCase();
    }

    // 🔹 Обробка текстових кнопок
    private void handleText(Long chatId, String text) throws TelegramApiException {
        List<String> categories = catalogSearcher.getCategories();

        if (categories.contains(text)) {
            currentCategory.put(chatId, text);
            currentSubcategory.remove(chatId);
            sendSubcategories(chatId, text);
            return;
        }

        if (currentCategory.containsKey(chatId)) {
            String cat = currentCategory.get(chatId);
            List<String> subcats = catalogSearcher.getSubcategories(cat);

            if (subcats.contains(text)) {
                currentSubcategory.put(chatId, text);
                productIndex.put(chatId, 0);
                sendProduct(chatId);
                return;
            }
        }

        sendText(chatId, "Невідома команда 😅 Натисніть /start або виберіть із меню.");
    }

    // --- Допоміжні методи для надсилання повідомлень ---
    private void sendText(Long chatId, String text) {
        sendText(chatId.toString(), text);
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboardMarkup markup) {
        sendMessage(chatId.toString(), text, markup);
    }

    private void sendMessage(String chatId, String text, ReplyKeyboardMarkup markup) {
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(markup)
                    .build();
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // --- Показ підкатегорій ---
    private void sendSubcategories(Long chatId, String category) throws TelegramApiException {
        List<String> subcats = catalogSearcher.getSubcategories(category);

        ReplyKeyboardMarkup markup = ReplyKeyboardMarkup.builder()
                .resizeKeyboard(true)
                .keyboard(buildKeyboard(subcats, true)) // <-- тепер кнопки по 3 в ряд
                .build();

        sendMessage(chatId, "📂 Виберіть підкатегорію:", markup);
    }

    // --- Показ товару ---
    private void sendProduct(Long chatId) throws TelegramApiException {
        String cat = currentCategory.get(chatId);
        String sub = currentSubcategory.get(chatId);
        int index = productIndex.getOrDefault(chatId, 0);

        List<Map<String, Object>> products = catalogSearcher.getProducts(cat, sub);
        if (products == null || products.isEmpty()) {
            sendText(chatId, "❌ У цій підкатегорії немає товарів.");
            return;
        }

        if (index >= products.size()) index = 0;

        Map<String, Object> product = products.get(index);
        lastShownProduct.put(chatId, product); // зберігаємо останній показаний товар

        String name = product.getOrDefault("name", "Без назви").toString();
        String price = product.getOrDefault("price", "N/A").toString();
        String unit = product.getOrDefault("unit", "шт").toString();
        String description = product.getOrDefault("description", "").toString();
        String photoPath = product.getOrDefault("photo", "").toString();
        String manufacturer = product.getOrDefault("manufacturer", "").toString();

        StringBuilder sb = new StringBuilder("📦 ").append(name)
                .append("\n💰 Ціна: ").append(price).append(" грн за ").append(unit);
        if (!manufacturer.isEmpty()) sb.append("\n🏭 Виробник: ").append(manufacturer);
        if (!description.isEmpty()) sb.append("\n📖 ").append(description);

        KeyboardRow row = new KeyboardRow();
        row.add("➡ Далі");
        row.add("🛒 Додати в кошик");
        row.add("🛒 Перейти в кошик");

        List<KeyboardRow> kb = new ArrayList<>();
        kb.add(row);
        kb.add(new KeyboardRow(List.of(new KeyboardButton("⬅ Назад"))));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(kb);

        if (photoPath != null && !photoPath.isEmpty()) {
            String fileName = new java.io.File(photoPath).getName();
            sendPhotoFromResources(chatId.toString(), fileName, sb.toString(), markup);
        } else {
            sendText(chatId.toString(), sb.toString());
        }

        // Збільшуємо індекс для наступного показу
        index = (index + 1) % products.size();
        productIndex.put(chatId, index);
    }

    private void sendPhoto(String chatId, String fileName, String caption) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("images/" + fileName);

            if (is == null) {
                System.out.println("[PHOTO] Файл не знайдено: " + fileName);
                sendText(chatId, "❌ Фото не знайдено.");
                return;
            }

            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId);

            // Створюємо InputFile з InputStream
            InputFile inputFile = new InputFile(is, fileName);
            photo.setPhoto(inputFile);

            photo.setCaption(caption);

            execute(photo);
            System.out.println("[PHOTO] Фото успішно надіслано: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "❌ Сталася помилка при відправці фото.");
        }
    }

    private void createOrderAdminMenu(String chatId, Map<String, Object> order, Long userId) {
        StringBuilder sb = new StringBuilder();

        sb.append("🆔 User ID: ").append(userId).append("\n")
                .append("🔢 Код замовлення: ").append(order.getOrDefault("orderCode", "-")).append("\n")
                .append("📦 Тип замовлення: ").append(order.getOrDefault("deliveryType", "Невідомо")).append("\n\n");

        String deliveryType = (String) order.get("deliveryType");
        if ("Самовивіз".equals(deliveryType)) {
            sb.append("🏙 Місто: ").append(order.getOrDefault("city", "-")).append("\n");
        } else if ("Доставка по місту".equals(deliveryType)) {
            sb.append("🏠 Адреса: ").append(order.getOrDefault("address", "-")).append("\n");
        } else if ("Нова пошта".equals(deliveryType)) {
            sb.append("📮 Відділення НП: ").append(order.getOrDefault("postOffice", "-")).append("\n");
        }

        sb.append("👤 П.І.: ").append(order.getOrDefault("fullName", "-")).append("\n")
                .append("📞 Телефон: ").append(order.getOrDefault("phone", "-")).append("\n")
                .append("💳 Картка: ").append(order.getOrDefault("card", "-")).append("\n\n");

        // Вивід товарів
        String itemsStr = (String) order.get("item");
        if (itemsStr != null && !itemsStr.isEmpty()) {
            String[] itemArr = itemsStr.split(";");
            int i = 1;
            for (String s : itemArr) {
                if (s.isBlank()) continue;
                String[] pair = s.split(":");
                String name = pair[0];
                double price = 0;
                try {
                    if (pair.length > 1) price = Double.parseDouble(pair[1]);
                } catch (Exception ignored) {}
                sb.append(i++).append(". 🛒 ").append(name).append(" — ").append(price).append(" грн\n");
            }
        }

        double total = 0.0;
        Object totalObj = order.get("total");
        if (totalObj instanceof Number) total = ((Number) totalObj).doubleValue();
        else if (totalObj != null) {
            try { total = Double.parseDouble(totalObj.toString()); } catch (Exception ignored) {}
        }
        sb.append("\n💰 Всього: ").append(total).append(" грн");

        // 🔹 Кнопки
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("✅ Підтвердити");
        row1.add("❌ Відхилити");
        row1.add("🗑️ Видалити замовлення");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("⏮️ Назад");
        row2.add("⏭️ Дальше");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("⬅️ Назад (Продавець меню)");
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(sb.toString());
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendText(chatId, "❌ Помилка при відправці повідомлення адміну.");
        }
    }

    // Допоміжний метод для створення клавіатури відгуку
    private ReplyKeyboardMarkup buildFeedbackKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("✉️ Відповісти на відгук");
        row1.add("💾 Зберегти відгук");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🧹 Видалити відгук");
        row2.add("⬅️ Назад в головне меню");
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        return markup;
    }

    // Головний метод створення меню відгуку
    private SendMessage createFeedbackSubMenu(String chatId, Long targetUserId) {
        ReplyKeyboardMarkup markup = buildFeedbackKeyboard();

        // Отримуємо останній відгук цього користувача
        List<String> feedbacks = FeedbackManager.getAllFeedbacks().get(targetUserId);
        String feedbackText = (feedbacks != null && !feedbacks.isEmpty())
                ? feedbacks.get(feedbacks.size() - 1)
                : "❌ Відгуків немає.";

        // Зберігаємо, щоб знати, кому відповідає адмін
        adminReplyTarget.put(Long.valueOf(chatId), targetUserId);

        return SendMessage.builder()
                .chatId(chatId)
                .text("Відгук користувача " + targetUserId + ":\n\n" + feedbackText + "\n\nОберіть дію:")
                .replyMarkup(markup)
                .build();
    }

    private void showAdminOrder(Long adminId, String chatId) {
        try (Connection conn = DatabaseManager.getConnection()) {

            // Беремо всі активні замовлення
            String sql = "SELECT * FROM orders WHERE status != 'Видалено' ORDER BY id ASC";
            List<Map<String, Object>> orders = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", rs.getInt("id"));
                    order.put("orderCode", rs.getString("orderCode"));
                    order.put("userId", rs.getLong("userId"));
                    order.put("deliveryType", rs.getString("deliveryType"));
                    order.put("city", rs.getString("city"));
                    order.put("address", rs.getString("address"));
                    order.put("postOffice", rs.getString("postOffice"));
                    order.put("fullName", rs.getString("fullName"));
                    order.put("phone", rs.getString("phone"));
                    order.put("card", rs.getString("card"));
                    order.put("status", rs.getString("status"));
                    order.put("date", rs.getDate("date"));
                    order.put("item", rs.getString("item"));

                    Object totalObj = rs.getObject("total");
                    double total = 0;
                    if (totalObj instanceof Number) total = ((Number) totalObj).doubleValue();
                    else if (totalObj != null) {
                        try { total = Double.parseDouble(totalObj.toString()); } catch (Exception ignored) {}
                    }
                    order.put("total", total);

                    orders.add(order);
                }
            }

            if (orders.isEmpty()) {
                sendText(chatId, "Замовлень немає.");
                return;
            }

            // Визначаємо який індекс показувати
            int idx = adminOrderIndex.getOrDefault(adminId, 0);
            if (idx >= orders.size()) idx = orders.size() - 1; // щоб не виходило за межі
            Map<String, Object> orderToShow = orders.get(idx);

            // Показуємо адміну
            createOrderAdminMenu(chatId, orderToShow, orderToShow.get("userId") instanceof Long ? (Long) orderToShow.get("userId") : 0L);

        } catch (SQLException e) {
            e.printStackTrace();
            sendText(chatId, "❌ Помилка при завантаженні замовлень з бази.");
        }
    }

    private void sendSearchedProduct(Long chatId) throws TelegramApiException {
        List<Map<String, Object>> results = searchResults.get(chatId);
        int index = productIndex.getOrDefault(chatId, 0);

        if (results == null || results.isEmpty()) {
            sendText(chatId, "❌ Немає результатів пошуку.");
            return;
        }

        if (index >= results.size()) index = 0;
        Map<String, Object> product = results.get(index);
        lastShownProduct.put(chatId, product);

        String name = product.getOrDefault("name", "Без назви").toString();
        String price = product.getOrDefault("price", "N/A").toString();
        String unit = product.getOrDefault("unit", "шт").toString();
        String description = product.getOrDefault("description", "").toString();
        String photoPath = product.getOrDefault("photo", "").toString();
        String category = product.getOrDefault("category", "").toString();
        String subcategory = product.getOrDefault("subcategory", "").toString();

        StringBuilder sb = new StringBuilder("📦 ").append(name)
                .append("\n💰 Ціна: ").append(price).append(" грн за ").append(unit);
        if (!category.isEmpty() || !subcategory.isEmpty()) {
            sb.append("\n📂 ").append(category);
            if (!subcategory.isEmpty()) sb.append(" → ").append(subcategory);
        }
        if (!description.isEmpty()) sb.append("\n📖 ").append(description);

        KeyboardRow row = new KeyboardRow();
        row.add("➡ Далі");
        row.add("🛒 Додати в кошик");

        List<KeyboardRow> kb = new ArrayList<>();
        kb.add(row);
        kb.add(new KeyboardRow(List.of(new KeyboardButton("⬅ Назад"))));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setKeyboard(kb);

        if (photoPath != null && !photoPath.isEmpty()) {
            String fileName = new java.io.File(photoPath).getName();
            sendPhotoFromResources(chatId.toString(), fileName, sb.toString(), markup);
        } else {
            sendText(chatId.toString(), sb.toString());
        }

        // Показуємо наступний товар
        index = (index + 1) % results.size();
        productIndex.put(chatId, index);
    }



    private void handleUserFeedback(Long userId, String chatId, String text) {
        userStates.remove(userId);

        feedbacks.computeIfAbsent(userId, k -> new ArrayList<>()).add(text);
        sendText(chatId, "✅ Дякуємо за ваш відгук!");

        // Надсилаємо розробникам
        for (Long devId : DEVELOPERS) {
            sendText(devId.toString(), "🆕 Новий відгук від користувача " + userId + ":\n\n" + text);
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        String chatId = callbackQuery.getMessage().getChatId().toString();
        Long devId = callbackQuery.getFrom().getId();

        try {
            if (data.startsWith("reply:")) {
                Long userId = Long.parseLong(data.split(":")[1]);
                replyTargets.put(devId, userId);
                userStates.put(devId, "waiting_for_reply");
                sendText(chatId, "✍️ Напишіть відповідь для користувача " + userId + ":");
            }

            else if (data.startsWith("save:")) {
                sendText(chatId, "✅ Відгук збережено (поки в пам’яті).");
            }

            else if (data.startsWith("delete:")) {
                String[] parts = data.split(":");
                Long userId = Long.parseLong(parts[1]);
                int hash = Integer.parseInt(parts[2]);

                List<String> list = feedbacks.get(userId);
                if (list != null) {
                    list.removeIf(f -> f.hashCode() == hash);
                    if (list.isEmpty()) feedbacks.remove(userId);
                }

                sendText(chatId, "🗑️ Відгук користувача " + userId + " видалено.");
            }
        } catch (Exception e) {
            sendText(chatId, "⚠️ Помилка при обробці дії.");
            e.printStackTrace();
        }
    }

    public void handleFeedbackState(Long userId, String chatId, String text, String state) throws TelegramApiException {
        switch (state) {
            case "waiting_for_feedback": // користувач пише відгук
                FeedbackManager.addFeedback(userId, text);
                sendText(chatId, "✅ Ваш відгук надіслано адміністратору!");
                userStates.remove(userId);
                break;

            case "writing_reply": // адмін пише відповідь
                Long targetUserId = adminReplyTarget.get(userId);
                if (targetUserId != null) {
                    sendText(targetUserId.toString(), "📩 Відповідь від адміністратора:\n" + text);
                    sendText(chatId, "✅ Відповідь надіслана користувачу " + targetUserId);
                } else {
                    sendText(chatId, "❌ Не знайдено користувача для відповіді.");
                }
                userStates.remove(userId);
                adminReplyTarget.remove(userId);
                break;
        }
    }

    private void sendPhotoFromResources(String chatId, String resourceFileName, String caption, ReplyKeyboardMarkup markup) {
        try {
            // Відносний шлях у src/main/resources, наприклад "images/фото.jpg"
            String resourcePath = "images/" + resourceFileName;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

            if (is == null) {
                System.out.println("[PHOTO] Фото не знайдено у ресурсах: " + resourcePath);
                sendText(chatId, "❌ Фото не знайдено: " + resourceFileName);
                return;
            }

            InputFile inputFile = new InputFile(is, resourceFileName); // InputStream + назва файлу

            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId);
            photo.setPhoto(inputFile);
            photo.setCaption(caption);
            photo.setReplyMarkup(markup);

            execute(photo);
            System.out.println("[PHOTO] Фото успішно надіслано: " + resourceFileName);

            // Закриваємо InputStream після відправки
            is.close();

        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "❌ Сталася помилка при відправленні фото.");
        }
    }

    public void handleFeedbackCallback(Update update) throws TelegramApiException {
        String data = update.getCallbackQuery().getData();
        Long adminId = update.getCallbackQuery().getFrom().getId();
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();

        if (data.startsWith("reply_")) {
            Long targetUserId = Long.parseLong(data.split("_")[1]);
            adminReplyTarget.put(adminId, targetUserId); // Map<Long, Long>
            userStates.put(adminId, "writing_reply");
            sendText(chatId, "✏️ Напишіть відповідь для користувача " + targetUserId + ":");

        } else if (data.startsWith("save_")) {
            FeedbackManager.saveFeedbacks();
            sendText(chatId, "💾 Відгук збережено у файлі.");

        } else if (data.startsWith("delete_")) {
            Long targetUserId = Long.parseLong(data.split("_")[1]);
            FeedbackManager.removeLastFeedback(targetUserId);
            sendText(chatId, "🧹 Відгук видалено.");
        }
    }

    private void notifyAllActiveUsersAboutHit() {
        for (Long userId : userStates.keySet()) {
            try {
                execute(SendMessage.builder()
                        .chatId(userId.toString())
                        .text("🌟 Новий Хіт продажу!")
                        .build());
            } catch (Exception e) {
                System.out.println("❌ Не вдалося надіслати користувачу " + userId);
            }
        }
    }
}