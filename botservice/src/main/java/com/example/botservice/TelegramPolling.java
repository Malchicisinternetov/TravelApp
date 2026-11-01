package com.example.botservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true")
public class TelegramPolling implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(TelegramPolling.class);
    private final TelegramClient telegram;
    private final BotCommands commands;
    private final Path offsetFile;
    private Thread worker;

    public TelegramPolling(TelegramClient telegram, BotCommands commands,
                           @Value("${bot.token}") String token, @Value("${bot.service-secret}") String secret,
                           @Value("${bot.offset-file}") String offsetFile) {
        if (token.isBlank() || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Set TELEGRAM_BOT_TOKEN and BOT_SERVICE_SECRET (32+ bytes)");
        }
        this.telegram = telegram;
        this.commands = commands;
        this.offsetFile = Path.of(offsetFile).toAbsolutePath();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker = new Thread(this::poll, "telegram-polling");
        worker.start();
    }

    private void poll() {
        long offset;
        try {
            offset = Files.exists(offsetFile) ? Long.parseLong(Files.readString(offsetFile).trim()) : 0;
        } catch (Exception ex) {
            log.error("Cannot read Telegram update cursor; polling stopped");
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                JsonNode response = telegram.updates(offset);
                if (response == null || !response.path("ok").asBoolean()) {
                    throw new IllegalStateException("Telegram getUpdates failed");
                }
                for (JsonNode update : response.path("result")) {
                    // Never retry a business command when delivering its reply fails.
                    offset = update.path("update_id").asLong() + 1;
                    try {
                        handle(update);
                    } finally {
                        try {
                            Files.createDirectories(offsetFile.getParent());
                            Files.writeString(offsetFile, Long.toString(offset));
                        } catch (IOException ex) {
                            log.error("Cannot save Telegram update cursor; polling stopped");
                            return;
                        }
                    }
                }
            } catch (Exception ex) {
                // HTTP exception messages can contain the Telegram token in the URL.
                log.warn("Telegram polling failed ({})", ex.getClass().getSimpleName());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    void handle(JsonNode update) {
        JsonNode message = update.path("message");
        if (!message.hasNonNull("text") || !message.hasNonNull("from")
                || message.path("from").path("is_bot").asBoolean()) return;
        long chatId = message.path("chat").path("id").asLong();
        if (!"private".equals(message.path("chat").path("type").asText())) {
            telegram.send(chatId, "Работа с поездками доступна в личном чате с ботом.");
            return;
        }
        long sender = message.path("from").path("id").asLong();
        if (sender <= 0) return;
        String name = message.path("from").path("first_name").asText("Путешественник");
        telegram.send(chatId, commands.execute(sender, name, message.path("text").asText()));
    }

    @Override
    public void destroy() {
        if (worker != null) worker.interrupt();
    }
}
