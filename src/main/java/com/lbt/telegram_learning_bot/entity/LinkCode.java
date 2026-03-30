package com.lbt.telegram_learning_bot.entity;

import com.lbt.telegram_learning_bot.platform.Platform;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

/**
 * Временный код для связывания аккаунтов между платформами.
 *
 * Пользователь вводит /link в боте A → получает 6-значный код.
 * Затем вводит /link <code> в боте B → аккаунты объединяются.
 *
 * Код действителен {@code EXPIRY_MINUTES} минут.
 */
@Entity
@Table(name = "link_codes")
@Data
@NoArgsConstructor
public class LinkCode {

    public static final int EXPIRY_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    /** Внутренний userId того, кто сгенерировал код. */
    @Column(name = "issuer_internal_user_id", nullable = false)
    private Long issuerInternalUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_platform", nullable = false, length = 20)
    private Platform issuerPlatform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
