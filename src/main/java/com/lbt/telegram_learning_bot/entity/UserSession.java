package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Data
@Entity
@Table(name = "user_session")
public class UserSession {
    @Id
    private Long userId;

    @Column(nullable = false)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String context;

    @Column(name = "updated_at")
    private Instant updatedAt;
}