package com.lbt.telegram_learning_bot.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.dto.*;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.CourseImportService;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;
import com.lbt.telegram_learning_bot.service.ZipCourseImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public class AdminHandler extends BaseHandler {

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
    private final AdminUserRepository adminUserRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final ObjectMapper objectMapper;
    private final KeyboardBuilder keyboardBuilder;

    public AdminHandler(MessageSender messageSender,
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
                        UserSettingsService userSettingsService) {
        super(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService);
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
        this.adminUserRepository = adminUserRepository;
        this.userProgressRepository = userProgressRepository;
        this.userStudyTimeRepository = userStudyTimeRepository;
        this.objectMapper = objectMapper;
        this.keyboardBuilder = keyboardBuilder;
    }

    // ================== Публичные методы ==================

    public void handleRetry(Long userId, Integer messageId) {
        BotState state = sessionService.getCurrentState(userId);
        if (state == BotState.AWAITING_COURSE_JSON) {
            promptCreateCourse(userId, messageId);
        } else if (state == BotState.AWAITING_IMAGE) {
            promptCurrentImage(userId, messageId);
        } else if (state == BotState.EDIT_TOPIC_JSON) {
            editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
        } else if (state == BotState.AWAITING_SECTION_JSON) {
            promptEditSectionJson(userId, messageId);
        }
    }

    private boolean isZipFile(byte[] content) {
        return content.length > 4 &&
                content[0] == 0x50 && content[1] == 0x4B &&
                content[2] == 0x03 && content[3] == 0x04;
    }

    private void processCourseZip(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            Course course = zipCourseImportService.importCourseFromZip(inputStream);
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.", createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        } catch (InvalidJsonException e) {
            log.warn("JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error importing course from ZIP", e);
            sendMessage(userId, "Ошибка импорта курса из ZIP: " + e.getMessage(), createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    public void handleDocument(Long userId, Object fileReference) {
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

            // Обычный JSON
            if (currentState == BotState.AWAITING_COURSE_JSON) {
                processCourseJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_COURSE_NAME_DESC) {
                processCourseNameDescJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_SECTION_NAME_DESC) {
                processSectionNameDescJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_TOPIC_JSON) {
                processTopicJson(userId, fileContent);
            } else if (currentState == BotState.AWAITING_SECTION_JSON) {
                processSectionJson(userId, fileContent);
            } else {
                sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
            }
        } catch (Exception e) {
            log.error("Error processing document for user {}", userId, e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    public void handleImageUpload(Long userId, Object fileReference) {
        if (fileReference == null) {
            sendMessage(userId, "Загрузка изображений через VK не поддерживается.", createCancelKeyboard());
            return;
        }
        sendMessage(userId, MSG_PLEASE_SEND_PHOTO, createCancelKeyboard());
    }

    public void promptCreateCourse(Long userId, Integer messageId) {
        String text = MSG_SEND_JSON_COURSE;
        if (messageId != null) {
            editMessage(userId, messageId, text, createCancelKeyboard());
        } else {
            sendMessage(userId, text, createCancelKeyboard());
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
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardForAdminBot(result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_EDIT);
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

        String text = MSG_WHAT_TO_CHANGE;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_SECTIONS, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_SECTIONS)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_EDIT_COURSE));
        editMessage(userId, messageId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_CHOOSE_ACTION);
    }

    public void handleSelectCourseForDelete(Long userId, Integer messageId, Long courseId) {
        String text = MSG_CONFIRM_DELETE_COURSE;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_YES_DELETE, CALLBACK_CONFIRM_DELETE_COURSE + ":" + courseId),
                BotButton.callback(BUTTON_NO, CALLBACK_EDIT_COURSE)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    public void handleEditCourseAction(Long userId, Integer messageId, String action, int pageSize) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_COURSE_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_NAME_DESC);
        } else if (ACTION_SECTIONS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            var result = navigationService.getSectionsPage(courseId, 0, pageSize);
            String text = MSG_SELECT_SECTION;
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        }
    }

    public void handleSelectSectionForEdit(Long userId, Integer messageId, Long sectionId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingSectionId(sectionId);
        sessionService.updateSessionContext(userId, context);

        String text = MSG_WHAT_TO_CHANGE_SECTION;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_TOPICS, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_TOPICS),
                BotButton.callback("📦 Заменить раздел (JSON)", CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_REPLACE_SECTION)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_ADMIN_BACK_TO_SECTIONS));
        editMessage(userId, messageId, text, keyboard);
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
            String text = MSG_SELECT_TOPIC;
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(
                    result, sectionId, CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
        } else if (ACTION_REPLACE_SECTION.equals(action)) {
            promptEditSectionJson(userId, messageId);
        }
    }

    private void promptEditSectionJson(Long userId, Integer messageId) {
        editMessage(userId, messageId, MSG_SEND_JSON_SECTION, createAdminCancelKeyboardWithBackToSections());
        sessionService.updateSessionState(userId, BotState.AWAITING_SECTION_JSON);
    }

    private void processSectionJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            SectionImportDto dto = objectMapper.readValue(inputStream, SectionImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            Section existingSection = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new RuntimeException("Section not found"));
            Section updatedSection = courseImportService.importSection(dto, existingSection);
            sendMessage(userId, "Раздел \"" + updatedSection.getTitle() + "\" успешно заменён.");
            Long courseId = updatedSection.getCourse().getId();
            context.setEditingCourseId(courseId);
            sessionService.updateSessionContext(userId, context);
            showEditCourseSectionsPage(userId, null, courseId, 0, ADMIN_DEFAULT_PAGE_SIZE);
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

    public void handleSelectTopicForEdit(Long userId, Integer messageId, Long topicId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingTopicId(topicId);
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic != null) {
            context.setEditingSectionId(topic.getSection().getId());
        }
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
            if (context.getCurrentCourseId() != null && context.getCurrentCourseId().equals(courseId)) {
                context.setCurrentCourseId(null);
                sessionService.updateSessionContext(userId, context);
            }
            editMessage(userId, messageId, MSG_COURSE_DELETED, createBackToMainKeyboard());
        } catch (Exception e) {
            log.error("Error deleting course", e);
            editMessage(userId, messageId, MSG_ERROR_DELETING_COURSE, createBackToMainKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    // ================== Внутренние методы обработки JSON ==================

    private void processCourseJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            Course course = courseImportService.importCourse(inputStream);
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.", createBackToMainKeyboard());
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

    private void processCourseNameDescJson(Long userId, byte[] fileContent) {
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            CourseNameDescDto dto = objectMapper.readValue(inputStream, CourseNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            if (courseId == null) {
                sendMessage(userId, MSG_COURSE_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Course oldCourse = courseRepository.findById(courseId).orElseThrow();
            String oldTitle = oldCourse.getTitle();
            String oldDesc = oldCourse.getDescription();
            Course updated = courseImportService.updateCourseNameDesc(courseId, dto.getTitle(), dto.getDescription());
            String response = String.format(FORMAT_COURSE_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription());
            sendMessage(userId, response, createBackToMainKeyboard());
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        } catch (Exception e) {
            log.error("Error processing course name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processSectionNameDescJson(Long userId, byte[] fileContent) {
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            SectionNameDescDto dto = objectMapper.readValue(inputStream, SectionNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            if (sectionId == null) {
                sendMessage(userId, MSG_SECTION_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Section oldSection = sectionRepository.findById(sectionId).orElseThrow();
            String oldTitle = oldSection.getTitle();
            String oldDesc = oldSection.getDescription();
            Section updated = courseImportService.updateSectionNameDesc(sectionId, dto.getTitle(), dto.getDescription());
            Long courseId = updated.getCourse().getId();
            String response = String.format(FORMAT_SECTION_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription());
            sendMessage(userId, response);
            context.setEditingCourseId(courseId);
            sessionService.updateSessionContext(userId, context);
            var result = navigationService.getSectionsPage(courseId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            String text = MSG_SELECT_SECTION;
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            sendMessage(userId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        } catch (Exception e) {
            log.error("Error processing section name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processTopicJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            TopicImportDto dto = objectMapper.readValue(inputStream, TopicImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long topicId = context.getEditingTopicId();
            Topic existingTopic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new RuntimeException("Topic not found"));
            Topic updatedTopic = courseImportService.importTopic(dto, existingTopic);
            sendMessage(userId, MSG_TOPIC_UPDATED);
            startImageUploadSequence(userId, null, updatedTopic.getId());
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

    // ================== Изображения ==================

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
        String text = String.format(MSG_IMAGE_REQUEST,
                idx + 1, pending.size(), next.getDescription());
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
        String courseTitle = navigationService.getCourseTitle(courseId);
        String text = String.format(FORMAT_EDIT_SECTIONS_HEADER,
                courseTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(result, courseId,
                CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
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
        String sectionTitle = navigationService.getSectionTitle(sectionId);
        String text = String.format(FORMAT_EDIT_TOPICS_HEADER,
                sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(result, sectionId,
                CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void handleBackToCoursesFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long editingCourseId = context.getEditingCourseId();
        if (editingCourseId != null) {
            showEditCoursesPage(userId, messageId, 0, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToSectionsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long courseId = context.getEditingCourseId();
        if (courseId != null) {
            Integer page = context.getAdminSectionsPage();
            if (page == null) page = 0;
            showEditCourseSectionsPage(userId, messageId, courseId, page, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long sectionId = context.getEditingSectionId();
        if (sectionId != null) {
            Integer page = context.getAdminTopicsPage();
            if (page == null) page = 0;
            showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
    }

    public boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
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

    // ================== Вспомогательные ==================

    private void sendJsonErrorWithBackToTopics(Long userId, String errorMessage) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long topicId = context.getEditingTopicId();
        Long sectionId = null;
        if (topicId != null) {
            Topic topic = topicRepository.findById(topicId).orElse(null);
            if (topic != null) {
                sectionId = topic.getSection().getId();
            }
        }
        Integer page = context.getAdminTopicsPage();
        if (sectionId == null) {
            sendMessage(userId, errorMessage, createBackToMainKeyboard());
            return;
        }
        String backCallback = CALLBACK_ADMIN_BACK_TO_TOPICS + ":" + sectionId + ":" + page;
        BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_RETRY, CALLBACK_RETRY))
                .addRow(BotButton.callback(BUTTON_CANCEL, backCallback));
        sendMessage(userId, errorMessage, keyboard);
    }

    private BotKeyboard createAdminCancelKeyboardWithBackToSections() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_ADMIN_BACK_TO_SECTIONS));
    }
}