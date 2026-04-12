package com.lbt.telegram_learning_bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
public class PaginationResult<T> {
    private List<T> items;
    private int currentPage;
    private int totalPages;
    private long totalItems;
    private boolean hasPrevious;
    private boolean hasNext;

    /** Создаёт PaginationResult из Spring Data {@link Page}. */
    public static <T> PaginationResult<T> of(Page<T> page) {
        return new PaginationResult<>(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.hasPrevious(),
                page.hasNext()
        );
    }

    /** Пустой результат для случаев, когда запрос не дал данных. */
    public static <T> PaginationResult<T> empty(int page) {
        return new PaginationResult<>(Collections.emptyList(), page, 0, 0, false, false);
    }
}
