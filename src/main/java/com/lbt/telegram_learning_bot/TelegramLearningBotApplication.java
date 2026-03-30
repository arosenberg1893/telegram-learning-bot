package com.lbt.telegram_learning_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@SpringBootApplication
@EnableScheduling
public class TelegramLearningBotApplication {
	public static void main(String[] args) {
		SpringApplication.run(TelegramLearningBotApplication.class, args);
	}
}