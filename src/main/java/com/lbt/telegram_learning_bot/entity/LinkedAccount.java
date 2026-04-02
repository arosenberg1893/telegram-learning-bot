package com.lbt.telegram_learning_bot.entity;

import com.lbt.telegram_learning_bot.platform.Platform;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Привязка платформенного аккаунта к единому внутреннему userId.
 *
 * Один пользователь может иметь несколько записей в этой таблице —
 * по одной на каждую платформу, где он зарегистрирован.
 *
 * Весь прогресс хранится по {@code internalUserId}.
 */
@Entity
@Table(name = "linked_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_platform_external",
                columnNames = {"platform", "external_user_id"}
        ))
@Data
@NoArgsConstructor
public class LinkedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Единый внутренний ID пользователя.
     * Совпадает с первым платформенным ID, с которым зарегистрировался пользователь.
     */
    @Column(name = "internal_user_id", nullable = false)
    private Long internalUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    /** ID пользователя в конкретной платформе (Telegram userId, VK user_id). */
    @Column(name = "external_user_id", nullable = false)
    private Long externalUserId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt = Instant.now();

    public LinkedAccount(Long internalUserId, Platform platform, Long externalUserId) {
        this.internalUserId = internalUserId;
        this.platform = platform;
        this.externalUserId = externalUserId;
        this.linkedAt = Instant.now();
    }
}
