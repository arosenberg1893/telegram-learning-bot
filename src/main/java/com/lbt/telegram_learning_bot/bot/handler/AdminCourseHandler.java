package com.lbt.telegram_learning_bot.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.dto.CourseNameDescDto;
import com.lbt.telegram_learning_bot.dto.SectionImportDto;
import com.lbt.telegram_learning_bot.dto.SectionNameDescDto;
import com.lbt.telegram_learning_bot.dto.TopicImportDto;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Обрабатывает административные операции с учебным контентом:
 * создание, редактирование и удаление курсов / разделов / тем,
 * импорт через JSON и ZIP, загрузка изображений.
 *
 * <p>Выделен из {@link AdminHandler} по принципу единой ответственности.
 * Все операции с бэкапами и режимом обслуживания находятся в
 * {@link AdminDatabaseHandler}.</p>
 */
@Slf4j
public class AdminCourseHandler extends BaseHandler {

    private final CourseImportService courseImportService;
    private final ZipCourseImportService zipCourseImportService;
    private final FileDownloader fileDownloader;
    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final TopicRepository topicRepository;
    private final BlockRepository blockRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final BlockImageRepository blockImageRepository;
    private final QuestionImageRepository questionImageRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final ObjectMapper objectMapper;
    private final KeyboardBuilder keyboardBuilder;
    private final ImageStorageService imageStorageService;

    public AdminCourseHandler(MessageSender messageSender,
                              FileDownloader fileDownloader,
                              UserSessionService sessionService,
                              NavigationService navigationService,
                              CourseImportService courseImportService,
                              ZipCourseImportService zipCourseImportService,
                              CourseRepository courseRepository,
                              KeyboardBuilder keyboardBuilder,
                              SectionRepository sectionRepository,
                              TopicRepository topicRepository,
                              BlockRepository blockRepository,
                              QuestionRepository questionRepository,
                              AnswerOptionRepository answerOptionRepository,
                              BlockImageRepository blockImageRepository,
                              QuestionImageRepository questionImageRepository,
                              AdminUserRepository adminUserRepository,
                              UserProgressRepository userProgressRepository,
                              UserStudyTimeRepository userStudyTimeRepository,
                              ObjectMapper objectMapper,
                              UserSettingsService userSettingsService,
                              MaintenanceModeService maintenanceModeService,
                              ImageStorageService imageStorageService) {
        super(messageSender, sessionService, navigationService,
                adminUserRepository, userSettingsService, maintenanceModeService);
        this.courseImportService = courseImportService;
        this.zipCourseImportService = zipCourseImportService;
        this.fileDownloader = fileDownloader;
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.topicRepository = topicRepository;
        this.blockRepository = blockRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.blockImageRepository = blockImageRepository;
        this.questionImageRepository = questionImageRepository;
        this.userProgressRepository = userProgressRepository;
        this.userStudyTimeRepository = userStudyTimeRepository;
        this.objectMapper = objectMapper;
        this.keyboardBuilder = keyboardBuilder;
        this.imageStorageService = imageStorageService;
    }

    // ================== Меню управления курсами ==================

    public void showCoursesManagementMenu(Long userId, Integer messageId) {
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_CREATE_COURSE, CALLBACK_CREATE_COURSE))
                .addRow(BotButton.callback(BUTTON_EDIT_COURSE, CALLBACK_EDIT_COURSE))
                .addRow(BotButton.callback(BUTTON_DELETE_COURSE, CALLBACK_DELETE_COURSE))
                .addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
        editMessage(userId, messageId, "Управление курсами:", keyboard);
    }

    // ================== Создание / редактирование курсов ==================

    public void promptCreateCourse(Long userId, Integer messageId) {
        if (messageId != null) {
            editMessage(userId, messageId, MSG_SEND_JSON_COURSE, createCancelKeyboard());
        } else {
            sendMessage(userId, MSG_SEND_JSON_COURSE, createCancelKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_COURSE_JSON);
    }

    public void promptEditCourse(Long userId, Integer messageId, int pageSize) {
        showEditCoursesPage(userId, messageId, 0, pageSize);
    }

    public void promptDeleteCourse(Long userId, Integer messageId, int pageSize) {
        showDeleteCoursesPage(userId, messageId, 0, pageSize);
    }

    public void promptCurrentImage(Long userId, Integer messageId) {
        requestNextImage(userId, messageId);
    }

    public void showEditCoursesPage(Long userId, Integer messageId, int page, int pageSize) {
        var result = navigationService.getAllCoursesPage(page, pageSize);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_EDIT, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_EDIT_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardForAdminBot(
                result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_EDIT);
        editMessage(userId, messageId, text, keyboard);
    }

    private void showDeleteCoursesPage(Long userId, Integer messageId, int page, int pageSize) {
        var result = navigationService.getAllCoursesPage(page, pageSize);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_DELETE, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_DELETE_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardForAdminBot(
                result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_DELETE);
        editMessage(userId, messageId, text, keyboard);
    }

    public void handleSelectCourseForEdit(Long userId, Integer messageId, Long courseId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingCourseId(courseId);
        sessionService.updateSessionContext(userId, context);

        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_SECTIONS, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_SECTIONS)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_EDIT_COURSE));
        editMessage(userId, messageId, MSG_WHAT_TO_CHANGE, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_CHOOSE_ACTION);
    }

    public void handleSelectCourseForDelete(Long userId, Integer messageId, Long courseId) {
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_YES_DELETE, CALLBACK_CONFIRM_DELETE_COURSE + ":" + courseId),
                BotButton.callback(BUTTON_NO, CALLBACK_EDIT_COURSE)
        );
        editMessage(userId, messageId, MSG_CONFIRM_DELETE_COURSE, keyboard);
    }

    public void handleEditCourseAction(Long userId, Integer messageId, String action, int pageSize) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_COURSE_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_NAME_DESC);
        } else if (ACTION_SECTIONS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            var result = navigationService.getSectionsPage(courseId, 0, pageSize);
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            editMessage(userId, messageId, MSG_SELECT_SECTION, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        }
    }

    public void handleSelectSectionForEdit(Long userId, Integer messageId, Long sectionId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingSectionId(sectionId);
        sessionService.updateSessionContext(userId, context);

        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_TOPICS, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_TOPICS),
                BotButton.callback("📦 Заменить раздел (JSON)", CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_REPLACE_SECTION)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_ADMIN_BACK_TO_SECTIONS));
        editMessage(userId, messageId, MSG_WHAT_TO_CHANGE_SECTION, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
    }

    public void handleEditSectionAction(Long userId, Integer messageId, String action, int pageSize) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_SECTION_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
        } else if (ACTION_TOPICS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            var result = navigationService.getTopicsPage(sectionId, 0, pageSize);
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(
                    result, sectionId, CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
            editMessage(userId, messageId, MSG_SELECT_TOPIC, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
        } else if (ACTION_REPLACE_SECTION.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_SECTION, createAdminCancelKeyboardWithBackToSections());
            sessionService.updateSessionState(userId, BotState.AWAITING_SECTION_JSON);
        }
    }

    public void handleSelectTopicForEdit(Long userId, Integer messageId, Long topicId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingTopicId(topicId);
        topicRepository.findById(topicId).ifPresent(t -> context.setEditingSectionId(t.getSection().getId()));
        sessionService.updateSessionContext(userId, context);

        editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
        sessionService.updateSessionState(userId, BotState.EDIT_TOPIC_JSON);
    }

    @Transactional
    public void handleConfirmDeleteCourse(Long userId, Integer messageId, Long courseId) {
        try {
            userStudyTimeRepository.deleteByCourseId(courseId);
            userProgressRepository.deleteByCourseId(courseId);
            courseRepository.deleteById(courseId);
            UserContext context = sessionService.getCurrentContext(userId);
            if (courseId.equals(context.getCurrentCourseId())) {
                context.setCurrentCourseId(null);
                sessionService.updateSessionContext(userId, context);
            }
            editMessage(userId, messageId, MSG_COURSE_DELETED, createBackToMainKeyboard());
        } catch (Exception e) {
            log.error("Error deleting course {}", courseId, e);
            editMessage(userId, messageId, MSG_ERROR_DELETING_COURSE, createBackToMainKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    public void handleRetry(Long userId, Integer messageId) {
        BotState state = sessionService.getCurrentState(userId);
        switch (state) {
            case AWAITING_COURSE_JSON -> promptCreateCourse(userId, messageId);
            case EDIT_TOPIC_JSON -> {
                editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
                sessionService.updateSessionState(userId, BotState.EDIT_TOPIC_JSON);
            }
            default -> sendMainMenu(userId, messageId);
        }
    }

    // ================== Обработка документов ==================

    /**
     * Маршрутизирует входящий файл на нужный обработчик в зависимости от текущего состояния сессии.
     * Файлы резервных копий ({@link BotState#AWAITING_BACKUP_FILE}) передаются
     * в {@link AdminDatabaseHandler} — этот метод их не обрабатывает.
     */
    public void handleDocument(Long userId, Object fileReference,
                               AdminDatabaseHandler dbHandler) {
        try {
            byte[] fileContent = fileDownloader.downloadFile(fileReference);
            BotState currentState = sessionService.getCurrentState(userId);

            if (isZipFile(fileContent)) {
                if (currentState == BotState.AWAITING_COURSE_JSON) {
                    processCourseZip(userId, fileContent);
                } else {
                    sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
                }
                return;
            }

            switch (currentState) {
                case AWAITING_COURSE_JSON    -> processCourseJson(userId, fileContent);
                case EDIT_COURSE_NAME_DESC   -> processCourseNameDescJson(userId, fileContent);
                case EDIT_SECTION_NAME_DESC  -> processSectionNameDescJson(userId, fileContent);
                case EDIT_TOPIC_JSON         -> processTopicJson(userId, fileContent);
                case AWAITING_SECTION_JSON   -> processSectionJson(userId, fileContent);
                case AWAITING_BACKUP_FILE    -> dbHandler.handleBackupFileUpload(userId, fileContent);
                default -> sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
            }
        } catch (Exception e) {
            log.error("Error processing document for user {}", userId, e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    // ================== Загрузка изображений ==================

    public void handleImageUpload(Long userId, Object fileReference) {
        if (sessionService.getCurrentState(userId) != BotState.AWAITING_IMAGE) return;
        if (fileReference == null) {
            sendMessage(userId, "Загрузка изображений через VK не поддерживается.", createCancelKeyboard());
            return;
        }
        try {
            byte[] imageBytes = fileDownloader.downloadFile(fileReference);
            saveImageToCurrentSlot(userId, imageBytes);
        } catch (Exception e) {
            log.error("Error processing image upload for user {}", userId, e);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
        }
    }

    private void saveImageToCurrentSlot(Long userId, byte[] imageBytes) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long entityId = context.getTargetEntityId();
        String entityType = context.getTargetEntityType();

        if (entityId == null || entityType == null) {
            log.warn("saveImageToCurrentSlot: entityId or entityType is null for user {}", userId);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
            return;
        }
        try {
            Path filePath = imageStorageService.saveImage(entityType, entityId, imageBytes);

            if (ENTITY_BLOCK.equals(entityType)) {
                blockImageRepository.findById(entityId).ifPresent(bi -> {
                    bi.setFilePath(filePath.toString());
                    blockImageRepository.save(bi);
                });
            } else if (ENTITY_QUESTION.equals(entityType)) {
                questionImageRepository.findById(entityId).ifPresent(qi -> {
                    qi.setFilePath(filePath.toString());
                    questionImageRepository.save(qi);
                });
            }

            context.setCurrentImageIndex(context.getCurrentImageIndex() + 1);
            sessionService.updateSessionContext(userId, context);
            requestNextImage(userId, null);
        } catch (Exception e) {
            log.error("Failed to save image for entity {} {}", entityType, entityId, e);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
        }
    }

    private void startImageUploadSequence(Long userId, Integer messageId, Long topicId) {
        List<BlockImage> blockImages = blockImageRepository.findPendingImagesByTopicId(topicId);
        List<QuestionImage> questionImages = questionImageRepository.findPendingImagesByTopicId(topicId);
        List<PendingImage> pending = new ArrayList<>();
        for (BlockImage bi : blockImages) {
            pending.add(new PendingImage(ENTITY_BLOCK, bi.getId(), bi.getDescription()));
        }
        for (QuestionImage qi : questionImages) {
            pending.add(new PendingImage(ENTITY_QUESTION, qi.getId(), qi.getDescription()));
        }
        if (pending.isEmpty()) {
            sendMessage(userId, MSG_TOPIC_UPDATED_NO_IMAGES);
            Long sectionId = sessionService.getCurrentContext(userId).getEditingSectionId();
            if (sectionId != null) {
                showEditTopicsPage(userId, null, sectionId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            } else {
                sendMainMenu(userId, null);
            }
            return;
        }
        UserContext context = sessionService.getCurrentContext(userId);
        context.setPendingImages(pending);
        context.setCurrentImageIndex(0);
        sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
        requestNextImage(userId, messageId);
    }

    private void requestNextImage(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        List<PendingImage> pending = context.getPendingImages();
        int idx = context.getCurrentImageIndex();
        if (idx >= pending.size()) {
            sendMessage(userId, MSG_IMAGES_COMPLETE);
            Long sectionId = context.getEditingSectionId();
            if (sectionId != null) {
                showEditTopicsPage(userId, null, sectionId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            } else {
                sendMainMenu(userId, null);
            }
            return;
        }
        PendingImage next = pending.get(idx);
        String text = String.format(MSG_IMAGE_REQUEST, idx + 1, pending.size(), next.getDescription());
        sendMessage(userId, text, createCancelKeyboard());
        context.setTargetEntityId(next.getEntityId());
        context.setTargetEntityType(next.getEntityType());
        sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
    }

    // ================== Административные страницы ==================

    public void showEditCourseSectionsPage(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminSectionsPage(page);
        sessionService.updateSessionContext(userId, context);

        var result = navigationService.getSectionsPage(courseId, page, pageSize);
        if (result.getItems().isEmpty()) {
            sendMessage(userId, "📭 В этом курсе нет разделов.", createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_SECTIONS_HEADER, page + 1, result.getTotalPages());
        BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void showEditTopicsPage(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminTopicsPage(page);
        sessionService.updateSessionContext(userId, context);

        var result = navigationService.getTopicsPage(sectionId, page, pageSize);
        if (result.getItems().isEmpty()) {
            sendMessage(userId, "📭 В этом разделе нет тем.", createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_TOPICS_HEADER, page + 1, result.getTotalPages());
        BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(
                result, sectionId, CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void handleBackToCoursesFromEdit(Long userId, Integer messageId, int pageSize) {
        showEditCoursesPage(userId, messageId, 0, pageSize);
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_CHOOSE_ACTION);
    }

    public void handleBackToSectionsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long courseId = context.getEditingCourseId();
        if (courseId != null) {
            showEditCourseSectionsPage(userId, messageId, courseId, 0, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long sectionId = context.getEditingSectionId();
        if (sectionId != null) {
            showEditTopicsPage(userId, messageId, sectionId, 0, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
        sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
        sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
    }

    public void handleAdminCoursesPage(Long userId, Integer messageId, String source, int page, int pageSize) {
        showEditCoursesPage(userId, messageId, page, pageSize);
    }

    public void handleAdminSectionsPage(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        showEditCourseSectionsPage(userId, messageId, courseId, page, pageSize);
    }

    public void handleAdminTopicsPage(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
    }

    // ================== Обработка JSON-файлов ==================

    private void processCourseJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            Course course = courseImportService.importCourse(new ByteArrayInputStream(fileContent));
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.",
                        createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        } catch (InvalidJsonException e) {
            log.warn("JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error importing course from JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    private void processCourseZip(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            Course course = zipCourseImportService.importCourseFromZip(new ByteArrayInputStream(fileContent));
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.",
                        createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        } catch (InvalidJsonException e) {
            log.warn("ZIP JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error importing course from ZIP", e);
            sendMessage(userId, "Ошибка импорта курса из ZIP: " + e.getMessage(), createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    private void processCourseNameDescJson(Long userId, byte[] fileContent) {
        try {
            CourseNameDescDto dto = objectMapper.readValue(
                    new ByteArrayInputStream(fileContent), CourseNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            if (courseId == null) {
                sendMessage(userId, MSG_COURSE_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Course oldCourse = courseRepository.findById(courseId).orElseThrow();
            Course updated = courseImportService.updateCourseNameDesc(courseId, dto.getTitle(), dto.getDescription());
            sendMessage(userId, String.format(FORMAT_COURSE_UPDATE,
                    oldCourse.getTitle(), updated.getTitle(),
                    oldCourse.getDescription(), updated.getDescription()),
                    createBackToMainKeyboard());
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        } catch (Exception e) {
            log.error("Error processing course name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processSectionNameDescJson(Long userId, byte[] fileContent) {
        try {
            SectionNameDescDto dto = objectMapper.readValue(
                    new ByteArrayInputStream(fileContent), SectionNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            if (sectionId == null) {
                sendMessage(userId, MSG_SECTION_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Section oldSection = sectionRepository.findById(sectionId).orElseThrow();
            Section updated = courseImportService.updateSectionNameDesc(sectionId, dto.getTitle(), dto.getDescription());
            sendMessage(userId, String.format(FORMAT_SECTION_UPDATE,
                    oldSection.getTitle(), updated.getTitle(),
                    oldSection.getDescription(), updated.getDescription()));
            context.setEditingCourseId(updated.getCourse().getId());
            sessionService.updateSessionContext(userId, context);
            var result = navigationService.getSectionsPage(updated.getCourse().getId(), 0, ADMIN_DEFAULT_PAGE_SIZE);
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, updated.getCourse().getId(), CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            sendMessage(userId, MSG_SELECT_SECTION, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        } catch (Exception e) {
            log.error("Error processing section name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processSectionJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            SectionImportDto dto = objectMapper.readValue(
                    new ByteArrayInputStream(fileContent), SectionImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            Section existing = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new RuntimeException("Section not found"));
            Section updated = courseImportService.importSection(dto, existing);
            sendMessage(userId, "Раздел \"" + updated.getTitle() + "\" успешно заменён.");
            context.setEditingCourseId(updated.getCourse().getId());
            sessionService.updateSessionContext(userId, context);
            showEditCourseSectionsPage(userId, null, updated.getCourse().getId(), 0, ADMIN_DEFAULT_PAGE_SIZE);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        } catch (InvalidJsonException e) {
            log.warn("Section JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error processing section JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    private void processTopicJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            TopicImportDto dto = objectMapper.readValue(
                    new ByteArrayInputStream(fileContent), TopicImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long topicId = context.getEditingTopicId();
            Topic existing = topicRepository.findById(topicId)
                    .orElseThrow(() -> new RuntimeException("Topic not found"));
            Topic updated = courseImportService.importTopic(dto, existing);
            sendMessage(userId, MSG_TOPIC_UPDATED);
            startImageUploadSequence(userId, null, updated.getId());
        } catch (InvalidJsonException e) {
            log.warn("Topic JSON validation error: {}", e.getMessage());
            sendJsonErrorWithBackToTopics(userId, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing topic JSON", e);
            sendJsonErrorWithBackToTopics(userId, MSG_JSON_PARSE_ERROR);
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    // ================== Вспомогательные ==================

    private void sendJsonErrorWithBackToTopics(Long userId, String errorMessage) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long topicId = context.getEditingTopicId();
        Long sectionId = null;
        if (topicId != null) {
            sectionId = topicRepository.findById(topicId)
                    .map(t -> t.getSection().getId()).orElse(null);
        }
        if (sectionId == null) {
            sendMessage(userId, errorMessage, createBackToMainKeyboard());
            return;
        }
        Integer page = context.getAdminTopicsPage();
        String backCallback = CALLBACK_ADMIN_BACK_TO_TOPICS + ":" + sectionId + ":" + page;
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_RETRY, CALLBACK_RETRY))
                .addRow(BotButton.callback(BUTTON_CANCEL, backCallback));
        sendMessage(userId, errorMessage, keyboard);
    }

    private BotKeyboard createAdminCancelKeyboardWithBackToSections() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_ADMIN_BACK_TO_SECTIONS));
    }

    private static boolean isZipFile(byte[] content) {
        return content.length > 4
                && content[0] == 0x50 && content[1] == 0x4B
                && content[2] == 0x03 && content[3] == 0x04;
    }
}
