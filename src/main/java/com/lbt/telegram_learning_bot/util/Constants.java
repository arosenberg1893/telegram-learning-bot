package com.lbt.telegram_learning_bot.util;

public final class Constants {
    private Constants() {}

    // ========== Типы тестов ==========
    public static final String TEST_TYPE_TOPIC = "TOPIC";
    public static final String TEST_TYPE_SECTION = "SECTION";
    public static final String TEST_TYPE_COURSE = "COURSE";
    public static final String TEST_TYPE_MISTAKE = "MISTAKE";

    // ========== Режимы сохранения прогресса ==========
    public static final String MODE_LEARNING = "learning";
    public static final String MODE_TEST = "test";

    // ========== Типы сущностей для изображений ==========
    public static final String ENTITY_BLOCK = "block";
    public static final String ENTITY_QUESTION = "question";

    // ========== Источники списков курсов ==========
    public static final String SOURCE_MY_COURSES = "my_courses";
    public static final String SOURCE_ALL_COURSES = "all_courses";
    public static final String SOURCE_SEARCH = "search";

    // ========== Действия редактирования (для колбэков админки) ==========
    public static final String ACTION_NAME_DESC = "name_desc";
    public static final String ACTION_SECTIONS = "sections";
    public static final String ACTION_TOPICS = "topics";

    // ========== Кнопки навигации (текст) ==========
    public static final String BUTTON_BACK = "🔙 Назад";
    public static final String BUTTON_MAIN_MENU = "🏠 Назад";
    public static final String BUTTON_CANCEL = "❌ Отмена";
    public static final String BUTTON_RETRY = "🔄 Повторить";
    public static final String BUTTON_NEXT = "Далее →";
    public static final String BUTTON_PREV = "◀ Назад";
    public static final String BUTTON_NEXT_PAGE = "Далее ▶";
    public static final String BUTTON_TO_QUESTIONS = "❓ К вопросам";
    public static final String BUTTON_NEXT_BLOCK = "⏩ Следующий блок";
    public static final String BUTTON_PREV_BLOCK = "⏪ Предыдущий блок";
    public static final String BUTTON_FINISH_TOPIC = "✅ Завершить тему";
    public static final String BUTTON_BACK_TO_TEXT = "Назад к тексту";
    public static final String BUTTON_EXIT_TOPIC = "Выйти из темы";
    public static final String BUTTON_COMPLETE = "✅ Завершить";
    public static final String BUTTON_RETRY_SEARCH = "🔁 Ввести заново";
    public static final String BUTTON_EXPORT_PDF = "📄 Экспорт PDF";
    public static final String BUTTON_BACK_TO_TOPICS_LIST = "📋 К списку тем";
    public static final String BUTTON_NAME_DESC = "Название / описание";
    public static final String BUTTON_SECTIONS = "Разделы курса";
    public static final String BUTTON_TOPICS = "Темы раздела";
    public static final String BUTTON_YES_DELETE = "Да, удалить";
    public static final String BUTTON_NO = "Нет";

    // ========== Кнопки главного меню ==========
    public static final String BUTTON_MY_COURSES = "📚 Мои курсы";
    public static final String BUTTON_ALL_COURSES = "📋 Выбрать курс";
    public static final String BUTTON_SEARCH = "🔍 Найти курс";
    public static final String BUTTON_STATISTICS = "📊 Статистика";
    public static final String BUTTON_MISTAKES = "❌ Мои ошибки";
    public static final String BUTTON_CREATE_COURSE = "➕ Создать курс";
    public static final String BUTTON_EDIT_COURSE = "✏️ Изменить курс";
    public static final String BUTTON_DELETE_COURSE = "🗑️ Удалить курс";

    // ========== Эмодзи для статусов ==========
    public static final String EMOJI_NOT_STARTED = "⚪";
    public static final String EMOJI_IN_PROGRESS = "🟡";
    public static final String EMOJI_COMPLETED = "🟢";
    public static final String EMOJI_FAILED = "🔴";

    // ========== Callback data (действия) ==========
    public static final String CALLBACK_MAIN_MENU = "main_menu";
    public static final String CALLBACK_MY_COURSES = "my_courses";
    public static final String CALLBACK_ALL_COURSES = "all_courses";
    public static final String CALLBACK_SEARCH_COURSES = "search_courses";
    public static final String CALLBACK_COURSES_PAGE = "courses_page";
    public static final String CALLBACK_SECTIONS_PAGE = "sections_page";
    public static final String CALLBACK_TOPICS_PAGE = "topics_page";
    public static final String CALLBACK_SELECT_COURSE = "select_course";
    public static final String CALLBACK_SELECT_SECTION = "select_section";
    public static final String CALLBACK_SELECT_TOPIC = "select_topic";
    public static final String CALLBACK_BACK_TO_COURSES = "back_to_courses";
    public static final String CALLBACK_BACK_TO_SECTIONS = "back_to_sections";
    public static final String CALLBACK_BACK_TO_TOPICS = "back_to_topics";
    public static final String CALLBACK_NEXT_BLOCK = "next_block";
    public static final String CALLBACK_PREV_BLOCK = "prev_block";
    public static final String CALLBACK_NEXT_QUESTION = "next_question";
    public static final String CALLBACK_PREV_QUESTION = "prev_question";
    public static final String CALLBACK_ANSWER = "answer";
    public static final String CALLBACK_TEST_TOPIC = "test_topic";
    public static final String CALLBACK_TEST_SECTION = "test_section";
    public static final String CALLBACK_TEST_COURSE = "test_course";
    public static final String CALLBACK_CREATE_COURSE = "create_course";
    public static final String CALLBACK_EDIT_COURSE = "edit_course";
    public static final String CALLBACK_DELETE_COURSE = "delete_course";
    public static final String CALLBACK_SELECT_COURSE_FOR_EDIT = "select_course_for_edit";
    public static final String CALLBACK_SELECT_COURSE_FOR_DELETE = "select_course_for_delete";
    public static final String CALLBACK_EDIT_COURSE_ACTION = "edit_course_action";
    public static final String CALLBACK_SELECT_SECTION_FOR_EDIT = "select_section_for_edit";
    public static final String CALLBACK_EDIT_SECTION_ACTION = "edit_section_action";
    public static final String CALLBACK_SELECT_TOPIC_FOR_EDIT = "select_topic_for_edit";
    public static final String CALLBACK_CONFIRM_DELETE_COURSE = "confirm_delete_course";
    public static final String CALLBACK_RETRY = "retry";
    public static final String CALLBACK_STATISTICS = "statistics";
    public static final String CALLBACK_EXPORT_PDF = "export_pdf";
    public static final String CALLBACK_MY_MISTAKES = "my_mistakes";
    public static final String CALLBACK_BACK = "back";
    public static final String CALLBACK_STATISTICS_BACK = "statistics:back";
    public static final String CALLBACK_BACK_TO_BLOCK_TEXT = "back_to_block_text";

    // ========== Сообщения пользователю ==========
    public static final String MSG_NO_DATA = "нет данных";
    public static final String MSG_WRONG_OPTION = "Ошибка: неверный вариант.";
    public static final String MSG_TOPIC_NO_QUESTIONS = "❓ В этой теме пока нет вопросов для тестирования. Попробуйте другую тему или вернитесь позже.";
    public static final String MSG_SECTION_NO_QUESTIONS = "❓ В этом разделе пока нет вопросов. Возможно, они появятся позже.";
    public static final String MSG_COURSE_NO_QUESTIONS = "❓ В этом курсе пока нет вопросов. Обратитесь к администратору.";
    public static final String MSG_NO_MISTAKES = "✅ Отлично! У вас пока нет ошибочных вопросов. Продолжайте в том же духе!";
    public static final String MSG_NO_MY_COURSES = "🌟 У вас пока нет пройденных курсов. Нажмите «Выбрать курс», чтобы начать обучение!";
    public static final String MSG_NO_COURSES = "📚 Пока нет ни одного курса. Обратитесь к администратору, если вы ожидали увидеть курсы.";
    public static final String MSG_TOPIC_NO_BLOCKS = "⚠️ В этой теме пока нет учебных блоков. Возможно, она ещё не наполнена. Вы можете вернуться к списку тем.";
    public static final String MSG_BLOCK_NOT_FOUND = "😕 Блок не найден. Возможно, он был удалён. Вернитесь к списку тем.";
    public static final String MSG_UNEXPECTED_FILE = "Неожиданный файл. Попробуйте снова.";
    public static final String MSG_SEND_JSON_COURSE = "Отправьте JSON-файл с данными курса.";
    public static final String MSG_SEND_JSON_COURSE_NAME_DESC = "Отправьте JSON с новым названием и описанием курса:";
    public static final String MSG_SEND_JSON_SECTION_NAME_DESC = "Отправьте JSON с новым названием и описанием раздела:";
    public static final String MSG_SEND_JSON_TOPIC = "Отправьте JSON с полным содержанием темы (блоки, вопросы, варианты):";
    public static final String MSG_WHAT_TO_CHANGE = "Что хотите изменить?";
    public static final String MSG_CONFIRM_DELETE_COURSE = "Вы уверены, что хотите удалить курс? Это действие необратимо.";
    public static final String MSG_COURSE_DELETED = "Курс успешно удалён.";
    public static final String MSG_ERROR_DELETING_COURSE = "Ошибка при удалении курса.";
    public static final String MSG_COURSE_IMPORT_SUCCESS = "Успешно. Курс \"%s\" добавлен. Изображения не требуются.";
    public static final String MSG_TOPIC_UPDATED = "Тема успешно обновлена.";
    public static final String MSG_IMAGES_COMPLETE = "Все изображения получены. Тема полностью обновлена.";
    public static final String MSG_IMAGE_REQUEST = "📸 Требуется изображение %d из %d:\n%s";
    public static final String MSG_SAVE_IMAGE_ERROR = "🖼️ Не удалось сохранить изображение. Проверьте формат файла (нужно фото) и повторите попытку.";
    public static final String MSG_PLEASE_SEND_PHOTO = "Пожалуйста, отправьте изображение.";
    public static final String MSG_NO_COURSES_TO_EDIT = "📭 Нет курсов для редактирования. Сначала создайте курс через «Создать курс».";
    public static final String MSG_NO_COURSES_TO_DELETE = "📭 Нет курсов для удаления.";
    public static final String MSG_SEARCH_NOT_FOUND = "😕 По запросу \"%s\" ничего не найдено.";
    public static final String MSG_JSON_PARSE_ERROR = "Ошибка распознавания файла. Проверьте формат JSON.";
    public static final String MSG_JSON_VALIDATION_ERROR = "Ошибки в JSON:\n%s";
    public static final String MSG_COURSE_NOT_SELECTED = "Ошибка: не выбран курс для редактирования.";
    public static final String MSG_SECTION_NOT_SELECTED = "Ошибка: не выбран раздел для редактирования.";
    public static final String MSG_PDF_ERROR = "❌ Ошибка при формировании PDF";
    public static final String STATS_PDF_CAPTION = "📊 Ваша статистика обучения";

    // ========== Форматы ==========
    public static final String FORMAT_COURSE_PROGRESS = "%s %s — %d%% (%d/%d вопросов)";
    public static final String FORMAT_TOPIC_COMPLETED = "Тема окончена.\nПравильных ответов: %d\nНеправильных ответов: %d\nВсего вопросов: %d";
    public static final String FORMAT_TEST_COMPLETED = "Тест завершён!\n\nПравильных ответов: %d\nНеправильных ответов: %d\nВсего вопросов: %d";
    public static final String FORMAT_QUESTION = "Вопрос %d из %d:\n\n%s";
    public static final String FORMAT_RESULT = "%s\n\nПояснение: %s";
    public static final String FORMAT_COURSE_UPDATE = "Успешно. Курс обновлён.\nПредыдущее название: %s\nНовое название: %s\nПредыдущее описание: %s\nНовое описание: %s";
    public static final String FORMAT_SECTION_UPDATE = "Успешно. Раздел обновлён.\nПредыдущее название: %s\nНовое название: %s\nПредыдущее описание: %s\nНовое описание: %s";
    public static final String FORMAT_SEARCH_RESULTS = "🔍 Результаты поиска по запросу «%s» (страница %d из %d) – найдено %d курсов.";
    public static final String FORMAT_MY_COURSES_HEADER = "📚 **Мои курсы** (страница %d из %d) – всего %d курсов.\nНажмите на курс, чтобы продолжить обучение.";
    public static final String FORMAT_ALL_COURSES_HEADER = "📋 **Все курсы** (страница %d из %d) – всего %d курсов.\nВыберите курс для изучения.";
    public static final String FORMAT_COURSE_SECTIONS_HEADER = "📖 **Курс:** %s\n%s\n\n⏱️ **Последний визит:** %s\n\n📌 **Разделы** (страница %d из %d) – всего %d разделов.\nВыберите раздел.";
    public static final String FORMAT_SECTIONS_HEADER = "📌 **Разделы** курса «%s» (страница %d из %d) – всего %d разделов.\n⏱️ **Последний визит:** %s";
    public static final String FORMAT_TOPICS_HEADER = "📂 **Раздел:** %s\n%s\n\n⏱️ **Последний визит:** %s\n\n📌 **Темы** (страница %d из %d) – всего %d тем.\nВыберите тему.";
    public static final String FORMAT_TOPICS_HEADER2 = "📌 **Темы** раздела «%s» (страница %d из %d) – всего %d тем.\n⏱️ **Последний визит:** %s";
    public static final String FORMAT_EDIT_COURSES_HEADER = "✏️ **Редактирование курсов** (страница %d из %d) – всего %d курсов.\nВыберите курс для изменения.";
    public static final String FORMAT_DELETE_COURSES_HEADER = "🗑️ **Удаление курсов** (страница %d из %d) – всего %d курсов.\nВыберите курс для удаления.";
    public static final String FORMAT_EDIT_SECTIONS_HEADER = "✏️ **Курс:** %s\n\nВыберите раздел для редактирования (страница %d из %d) – всего %d разделов.";
    public static final String FORMAT_EDIT_TOPICS_HEADER = "✏️ **Раздел:** %s\n\nВыберите тему для редактирования (страница %d из %d) – всего %d тем.";

    public static final String STATS_TITLE = "📊 Ваша статистика:";
    public static final String STATS_TOTAL_COURSES = "Всего курсов: %d\n";
    public static final String STATS_COMPLETED = "Полностью пройдено: %d\n";
    public static final String STATS_HARDEST = "Самый трудный курс: %s\n\n";
    public static final String STATS_TOTAL_TIME = "⏱️ Общее время изучения: %s\n";
    public static final String STATS_PROGRESS = "📚 Прогресс по курсам:\n";
    public static final String STATS_NO_DATA = "   (нет данных)";


    public static final String MSG_SEARCH_PROMPT = "🔍 Введите текст для поиска курсов:";
    public static final String FORMAT_COURSE_HEADER = "📖 **Курс:** %s\n\n%s\n\n⏱️ **Последний визит:** %s";
    public static final String FORMAT_SECTION_HEADER = "📂 **Раздел:** %s\n\n%s\n\n⏱️ **Последний визит:** %s";
    public static final String MSG_SELECT_SECTION = "Выберите раздел для редактирования:";
    public static final String MSG_WHAT_TO_CHANGE_SECTION = "Что хотите изменить в разделе?";
    public static final String MSG_SELECT_TOPIC = "Выберите тему для редактирования:";
    public static final String MSG_TOPIC_UPDATED_NO_IMAGES = "Тема обновлена. Изображения не требуются.";
    public static final String DEFAULT_USER_NAME = "Пользователь";
    public static final String MSG_MAIN_MENU = "Главное меню:";



    public static final String MSG_SEARCH_RESULTS_HEADER = "Результаты поиска (страница 1):";
    public static final String MSG_PROGRESS = "⏳ Обрабатываю файл...";
    public static final String PDF_TITLE = "Статистика обучения";
    public static final String PDF_USER_LABEL = "Пользователь: ";
    public static final String PDF_DATE_LABEL = "Дата: ";
    public static final String PDF_STATS_GENERAL = "Общая статистика:";
    public static final String PDF_TOTAL_STARTED = "• Всего начато курсов: ";
    public static final String PDF_COMPLETED = "• Полностью пройдено: ";
    public static final String PDF_HARDEST = "• Самый трудный курс: ";
    public static final String PDF_TOTAL_TIME = "⏱️ Общее время изучения: ";
    public static final String PDF_PROGRESS = "Прогресс по курсам:";
    public static final String PDF_COLUMN_COURSE = "Курс";
    public static final String PDF_COLUMN_PROGRESS = "Прогресс";
    public static final String PDF_COLUMN_STATUS = "Статус";
    public static final String PDF_STATUS_NOT_STARTED = "Не начат";
    public static final String PDF_STATUS_IN_PROGRESS = "В процессе";
    public static final String PDF_STATUS_COMPLETED = "Завершён";
    public static final String TIME_JUST_NOW = "только что";
    public static final String TIME_MINUTES_AGO = " мин назад";
    public static final String TIME_HOURS_AGO = " ч назад";
    public static final String TIME_DAYS_AGO = " дн назад";

    public static final String MSG_CORRECT = "✅ Верно!";
    public static final String MSG_WRONG = "❌ Неверно.";

    public static final String TOO_MANY_REQUEST = "⏳ Слишком много запросов. Пожалуйста, подождите минуту.";

    public static final String CALLBACK_ADMIN_COURSES_PAGE = "admin_courses_page";
    public static final String CALLBACK_ADMIN_SECTIONS_PAGE = "admin_sections_page";
    public static final String CALLBACK_ADMIN_TOPICS_PAGE = "admin_topics_page";
    public static final String CALLBACK_ADMIN_BACK_TO_SECTIONS = "admin_back_to_sections";
    public static final String CALLBACK_ADMIN_BACK_TO_TOPICS = "admin_back_to_topics";

    public static final String CALLBACK_SETTINGS = "settings";
    public static final String CALLBACK_SETTINGS_SHUFFLE = "settings_shuffle";
    public static final String CALLBACK_SETTINGS_PAGESIZE = "settings_pagesize";
    public static final String CALLBACK_SETTINGS_QUESTIONS = "settings_questions";
    public static final String CALLBACK_SETTINGS_EXPLANATIONS = "settings_explanations";
    public static final String CALLBACK_SETTINGS_NOTIFICATIONS = "settings_notifications";
    public static final String CALLBACK_SETTINGS_RESET = "settings_reset";
    public static final int ADMIN_PAGE_SIZE = 5;

    // ========== Привязка аккаунтов ==========
    public static final String CALLBACK_LINK_GENERATE        = "link_generate";
    public static final String CALLBACK_LINK_RESOLVE_KEEP_THIS  = "link_keep_this";
    public static final String CALLBACK_LINK_RESOLVE_KEEP_OTHER = "link_keep_other";

    public static final String MSG_LINK_CODE_GENERATED =
            "🔗 Ваш код для привязки аккаунта: *%s*\n\n" +
            "Откройте бот в другой соц. сети и введите:\n`/link %s`\n\n" +
            "Код действителен %d минут.";
    public static final String MSG_LINK_INVALID_CODE =
            "❌ Код не найден или истёк. Попробуйте сгенерировать новый командой /link";
    public static final String MSG_LINK_SAME_PLATFORM =
            "❌ Нельзя привязать аккаунты одной и той же платформы.";
    public static final String MSG_LINK_ALREADY_LINKED =
            "✅ Эти аккаунты уже связаны!";
    public static final String MSG_LINK_ALREADY_ELSEWHERE =
            "❌ Ваш аккаунт уже привязан к другому пользователю.";
    public static final String MSG_LINK_SUCCESS =
            "✅ Аккаунты успешно объединены! Прогресс синхронизирован.";
    public static final String MSG_LINK_CONFLICT =
            "⚠️ У вас есть прогресс в обоих аккаунтах.\n\n" +
            "Выберите, *чей прогресс сохранить* (второй будет удалён):";
    public static final String MSG_LINK_STATUS_LINKED =
            "🔗 Аккаунты привязаны: %s";
    public static final String MSG_LINK_STATUS_NOT_LINKED =
            "🔓 Другие платформы не привязаны. Используйте /link для связывания.";
    public static final String BUTTON_KEEP_THIS_PROGRESS  = "Оставить прогресс этого аккаунта";
    public static final String BUTTON_KEEP_OTHER_PROGRESS = "Оставить прогресс другого аккаунта";

    // ========== Дополнительные форматы ==========
    public static final String FORMAT_STUDY_TIME_HOURS = "%d ч %d мин";
    public static final String FORMAT_STUDY_TIME_MINUTES = "%d мин";

    // ========== Callback data настроек (с параметрами) ==========
    public static final String CALLBACK_SETTINGS_PAGESIZE_SET = "settings_pagesize_set";
    public static final String CALLBACK_SETTINGS_QUESTIONS_SET = "settings_questions_set";
    public static final String CALLBACK_SETTINGS_RESET_CONFIRM = "settings_reset_confirm";
    public static final String CALLBACK_SETTINGS_PAGESIZE_OTHER = "settings_pagesize_other";

    public static final int ADMIN_DEFAULT_PAGE_SIZE = 5;
}
