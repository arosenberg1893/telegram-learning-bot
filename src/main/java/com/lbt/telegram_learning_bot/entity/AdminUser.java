package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

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