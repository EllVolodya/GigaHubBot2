package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // Підключення до бази даних
        DatabaseManager.connect();

        // Перевірка підключення
        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("✅ Database is connected successfully!");
            } else {
                System.out.println("❌ Database connection failed!");
            }
        } catch (SQLException e) {
            System.out.println("❌ Error while checking database connection!");
            e.printStackTrace();
        }

        try {
            String token = System.getenv("BOT_TOKEN");
            if (token == null || token.isBlank()) {
                logger.severe("BOT_TOKEN environment variable is not set. Exiting.");
                System.err.println("ERROR: BOT_TOKEN environment variable is not set.");
                DatabaseManager.disconnect();
                System.exit(1);
            }

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            StoreBot bot = new StoreBot(token);
            botsApi.registerBot(bot);

            logger.info("Bot started successfully!");
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, "Failed to start bot", e);
        } finally {
            // Закриваємо підключення при завершенні програми
            Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::disconnect));
        }
    }
}