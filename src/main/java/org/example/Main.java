package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static myBot botInstance;

    public static void main(String[] args) {
        try {
            botInstance = new myBot();
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(botInstance);
            System.out.println("Bot started.");

            Launcher.show(botInstance);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
