package com.lbt.telegram_learning_bot.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Вспомогательный класс для безопасного разбора строк callback-данных.
 *
 * <p>Формат callback: {@code "action"} или {@code "action:part1"} или
 * {@code "action:part1:part2"}. Разделитель — двоеточие, максимум 3 части
 * ({@code split(":", 3)}).</p>
 *
 * <p>Все методы {@code getLong}/{@code getInt}/{@code getString} возвращают
 * {@code Optional} и никогда не бросают исключений — некорректный ввод
 * просто возвращает {@code Optional.empty()}, что позволяет обработчику
 * отреагировать осмысленно вместо падения с NPE / AIOOBE.</p>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * CallbackData cb = CallbackData.parse(data);
 * switch (cb.action()) {
 *     case CALLBACK_SELECT_COURSE ->
 *         cb.getLong(1).ifPresent(id -> handleSelectCourse(userId, msgId, id, pageSize));
 *     case CALLBACK_COURSES_PAGE ->
 *         cb.getString(1).ifPresent(src ->
 *             cb.getInt(2).ifPresent(page ->
 *                 handleCoursesPage(userId, msgId, src, page, pageSize)));
 * }
 * }</pre>
 */
@Slf4j
public final class CallbackData {

    private final String[] parts;

    private CallbackData(String[] parts) {
        this.parts = parts;
    }

    /**
     * Разбирает строку callback-данных на части.
     * Никогда не возвращает {@code null}; при пустой строке возвращает объект
     * с единственной пустой частью.
     */
    public static CallbackData parse(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("Received empty callback data");
            return new CallbackData(new String[]{""});
        }
        return new CallbackData(raw.split(":", 3));
    }

    /** Возвращает первую часть (action). Никогда не {@code null}. */
    public String action() {
        return parts[0];
    }

    /**
     * Возвращает часть по индексу как строку, или {@code Optional.empty()}
     * если индекс выходит за пределы.
     *
     * @param index 0-based индекс (0 = action, 1 = первый параметр, 2 = второй)
     */
    public Optional<String> getString(int index) {
        if (index < 0 || index >= parts.length) return Optional.empty();
        return Optional.of(parts[index]);
    }

    /**
     * Возвращает часть по индексу как {@code Long}, или {@code Optional.empty()}
     * если часть отсутствует или не является числом.
     */
    public Optional<Long> getLong(int index) {
        return getString(index).flatMap(s -> {
            try {
                return Optional.of(Long.parseLong(s));
            } catch (NumberFormatException e) {
                log.warn("Cannot parse Long from callback part[{}]='{}' in data '{}'",
                        index, s, String.join(":", parts));
                return Optional.empty();
            }
        });
    }

    /**
     * Возвращает часть по индексу как {@code Integer}, или {@code Optional.empty()}
     * если часть отсутствует или не является числом.
     */
    public Optional<Integer> getInt(int index) {
        return getString(index).flatMap(s -> {
            try {
                return Optional.of(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                log.warn("Cannot parse Integer from callback part[{}]='{}' in data '{}'",
                        index, s, String.join(":", parts));
                return Optional.empty();
            }
        });
    }

    /** Количество частей (минимум 1). */
    public int size() {
        return parts.length;
    }

    @Override
    public String toString() {
        return String.join(":", parts);
    }
}
