package com.lbt.telegram_learning_bot.config;

import com.lbt.telegram_learning_bot.entity.AdminUser;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;

    @Value("${admin.user-id:}")
    private Long adminTelegramId;

    @Override
    public void run(String... args) throws Exception {
        if (adminTelegramId == null || adminTelegramId == 0) {
            log.warn("ADMIN_TELEGRAM_ID environment variable not set. No admin user will be created.");
            return;
        }
        if (!adminUserRepository.existsByUserId(adminTelegramId)) {
            AdminUser admin = new AdminUser();
            admin.setUserId(adminTelegramId);
            adminUserRepository.save(admin);
            log.info("Admin user with Telegram ID {} has been created.", adminTelegramId);
        } else {
            log.info("Admin user with Telegram ID {} already exists.", adminTelegramId);
        }
    }
}
