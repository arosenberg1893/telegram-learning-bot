package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_mistake", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserMistake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // Telegram ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @UpdateTimestamp
    @Column(name = "last_mistake_at")
    private Instant lastMistakeAt;
}