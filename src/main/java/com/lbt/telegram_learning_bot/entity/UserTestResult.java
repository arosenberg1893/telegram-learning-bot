package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_test_result", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "test_type", "test_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserTestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "test_type", nullable = false, length = 20)
    private String testType; // TEST_TYPE_TOPIC, TEST_TYPE_SECTION, TEST_TYPE_COURSE

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}