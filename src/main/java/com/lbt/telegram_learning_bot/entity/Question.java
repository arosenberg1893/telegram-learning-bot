package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.ENTITY_QUESTION;

@Entity
@Table(name = ENTITY_QUESTION)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private Block block;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = ENTITY_QUESTION, cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuestionImage> images = new ArrayList<>();

    @OneToMany(mappedBy = ENTITY_QUESTION, cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AnswerOption> answerOptions = new ArrayList<>();
}