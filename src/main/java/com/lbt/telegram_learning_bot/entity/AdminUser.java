package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Entity
@Table(name = "admin_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {

    @Id
    @Column(name = "user_id")
    private Long userId; // Telegram ID

    @CreationTimestamp
    @Column(name = "granted_at", updatable = false)
    private Instant grantedAt;
}