package com.lbt.telegram_learning_bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Data
@AllArgsConstructor
public class PaginationResult<T> {
    private List<T> items;
    private int currentPage;
    private int totalPages;
    private long totalItems;          // изменено с int на long
    private boolean hasPrevious;
    private boolean hasNext;
}