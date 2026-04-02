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

@Entity
@Table(name = "course")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Так как у нас BIGSERIAL
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Связь с разделами (один ко многим)
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Section> sections = new ArrayList<>();

    // Вспомогательные методы для добавления/удаления разделов
    public void addSection(Section section) {
        sections.add(section);
        section.setCourse(this);
    }

    public void removeSection(Section section) {
        sections.remove(section);
        section.setCourse(null);
    }
}