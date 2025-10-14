package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        try {
            String token = System.getenv("BOT_TOKEN");
            if (token == null || token.isBlank()) {
                logger.severe("BOT_TOKEN environment variable is not set. Exiting.");
                System.err.println("ERROR: BOT_TOKEN environment variable is not set.");
                System.exit(1);
            }

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            StoreBot bot = new StoreBot(token);
            botsApi.registerBot(bot);

            logger.info("Bot started successfully!");
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, "Failed to start bot", e);
        }
    }
}