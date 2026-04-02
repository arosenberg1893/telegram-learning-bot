package com.lbt.telegram_learning_bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {
    @Id
    private Long userId;

    private Boolean shuffleOptions = true;          // перемешивать варианты ответов
    private Integer pageSize = 5;                   // количество элементов на странице
    private Integer testQuestionsPerBlock = 2;      // вопросов на блок в тестах раздела/курса
    private Boolean showExplanations = true;        // показывать пояснения после ответа
    private Boolean notificationsEnabled = false;    // уведомления о новых курсах
}