package com.lbt.telegram_learning_bot.bot;

public enum BotState {
    MAIN_MENU,
    MY_COURSES,
    ALL_COURSES,
    SEARCH_COURSES,
    AWAITING_SEARCH_QUERY,
    COURSE_SECTIONS,
    SECTION_TOPICS,
    TOPIC_BLOCKS,
    TOPIC_LEARNING,
    BLOCK_CONTENT,
    QUESTION,
    TEST_RESULT,
    // Административные состояния
    ADMIN_MENU,
    AWAITING_COURSE_JSON,
    AWAITING_IMAGE,
    AWAITING_COURSE_SELECTION_FOR_EDIT,
    AWAITING_SECTION_SELECTION_FOR_EDIT,
    AWAITING_TOPIC_SELECTION_FOR_EDIT,
    EDIT_COURSE_NAME_DESC,
    EDIT_SECTION_NAME_DESC,
    EDIT_TOPIC_JSON,
    AWAITING_COURSE_SELECTION_FOR_DELETE,
    EDIT_COURSE_CHOOSE_ACTION,
    EDIT_COURSE_SECTION_CHOOSE,
    EDIT_SECTION_CHOOSE_TOPIC,
    AWAITING_IMAGES,
    AWAITING_LINK_CODE,
    AWAITING_PAGE_SIZE_INPUT,
    AWAITING_SECTION_JSON,
    AWAITING_BACKUP_FILE;

    /**
     * Возвращает {@code true}, если состояние относится к потоку администрирования курсов.
     * Используется в диспетчерах Telegram и VK для корректной маршрутизации кнопки «Назад».
     */
    public boolean isAdminEditState() {
        return switch (this) {
            case EDIT_COURSE_SECTION_CHOOSE,
                 EDIT_SECTION_CHOOSE_TOPIC,
                 EDIT_COURSE_NAME_DESC,
                 EDIT_SECTION_NAME_DESC,
                 EDIT_TOPIC_JSON,
                 AWAITING_IMAGE,
                 AWAITING_COURSE_JSON,
                 AWAITING_SECTION_JSON,
                 AWAITING_BACKUP_FILE -> true;
            default -> false;
        };
    }
}
