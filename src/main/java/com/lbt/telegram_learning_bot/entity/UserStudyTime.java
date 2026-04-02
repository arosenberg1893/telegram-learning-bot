package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_study_time", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "topic_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserStudyTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "total_seconds", nullable = false)
    private int totalSeconds;

    @UpdateTimestamp
    @Column(name = "last_action_at")
    private Instant lastActionAt;
}