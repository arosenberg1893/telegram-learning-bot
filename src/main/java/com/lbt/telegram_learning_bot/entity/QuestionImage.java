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
@Table(name = "question_image")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(length = 255)
    private String description;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}