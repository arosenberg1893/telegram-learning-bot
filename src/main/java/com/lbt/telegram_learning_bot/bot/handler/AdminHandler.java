package com.lbt.telegram_learning_bot.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.dto.CourseNameDescDto;
import com.lbt.telegram_learning_bot.dto.SectionNameDescDto;
import com.lbt.telegram_learning_bot.dto.TopicImportDto;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.CourseImportService;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.GetFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
public class AdminHandler extends BaseHandler {

    private final CourseImportService courseImportService;
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
    private final ObjectMapper objectMapper;
    private final KeyboardBuilder keyboardBuilder;

    public AdminHandler(TelegramBot telegramBot,
                        UserSessionService sessionService,
                        NavigationService navigationService,
                        CourseImportService courseImportService,
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
                        ObjectMapper objectMapper) {
        super(telegramBot, sessionService, navigationService, adminUserRepository);
        this.courseImportService = courseImportService;
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
        this.objectMapper = objectMapper;
        this.keyboardBuilder=keyboardBuilder;
    }

    // ================== Публичные методы для диспетчера ==================
    public void handleCreateCourse(Long userId, Integer messageId) {
        promptCreateCourse(userId, messageId);
    }

    public void handleEditCourse(Long userId, Integer messageId) {
        promptEditCourse(userId, messageId);
    }

    public void handleDeleteCourse(Long userId, Integer messageId) {
        promptDeleteCourse(userId, messageId);
    }

    public void handleSelectCourseForEdit(Long userId, Integer messageId, Long courseId) {
        handleSelectCourseForEditInternal(userId, messageId, courseId);
    }

    public void handleSelectCourseForDelete(Long userId, Integer messageId, Long courseId) {
        handleSelectCourseForDeleteInternal(userId, messageId, courseId);
    }

    public void handleEditCourseAction(Long userId, Integer messageId, String action) {
        handleEditCourseActionInternal(userId, messageId, action);
    }

    public void handleSelectSectionForEdit(Long userId, Integer messageId, Long sectionId) {
        handleSelectSectionForEditInternal(userId, messageId, sectionId);
    }

    public void handleEditSectionAction(Long userId, Integer messageId, String action) {
        handleEditSectionActionInternal(userId, messageId, action);
    }

    public void handleSelectTopicForEdit(Long userId, Integer messageId, Long topicId) {
        handleSelectTopicForEditInternal(userId, messageId, topicId);
    }

    public void handleConfirmDeleteCourse(Long userId, Integer messageId, Long courseId) {
        handleConfirmDeleteCourseInternal(userId, messageId, courseId);
    }

    public void handleRetry(Long userId, Integer messageId) {
        BotState state = sessionService.getCurrentState(userId);
        if (state == BotState.AWAITING_COURSE_JSON) {
            promptCreateCourse(userId, messageId);
        } else if (state == BotState.AWAITING_IMAGE) {
            promptCurrentImage(userId, messageId);
        } else if (state == BotState.EDIT_TOPIC_JSON) {
            // Повторно запрашиваем JSON для темы
            editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
            // состояние остаётся EDIT_TOPIC_JSON
        }
    }

    public void handleCourseNameDescJson(Long userId, Message message) {
        handleCourseNameDescJsonInternal(userId, message);
    }

    public void handleSectionNameDescJson(Long userId, Message message) {
        handleSectionNameDescJsonInternal(userId, message);
    }

    public void handleTopicJson(Long userId, Message message) {
        handleTopicJsonInternal(userId, message);
    }

    public void handleDocument(Long userId, Message message) {
        BotState currentState = sessionService.getCurrentState(userId);
        var document = message.document();
        if (document == null) return;

        if (currentState == BotState.AWAITING_COURSE_JSON) {
            handleCourseJson(userId, message);
        } else if (currentState == BotState.EDIT_COURSE_NAME_DESC) {
            handleCourseNameDescJson(userId, message);
        } else if (currentState == BotState.EDIT_SECTION_NAME_DESC) {
            handleSectionNameDescJson(userId, message);
        } else if (currentState == BotState.EDIT_TOPIC_JSON) {
            handleTopicJson(userId, message);
        } else if (currentState == BotState.AWAITING_IMAGE) {
            handleImageUpload(userId, message);
        } else {
            sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
        }
    }

    public void handleImageUpload(Long userId, Message message) {
        handleImageUploadInternal(userId, message);
    }

    // ================== Внутренние методы ==================
    public void promptCreateCourse(Long userId, Integer messageId) {
        String text = MSG_SEND_JSON_COURSE;
        if (messageId != null) {
            editMessage(userId, messageId, text, createCancelKeyboard());
        } else {
            sendMessage(userId, text, createCancelKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_COURSE_JSON);
    }

    public void promptEditCourse(Long userId, Integer messageId) {
        showEditCoursesPage(userId, messageId, 0);
    }

    public void promptDeleteCourse(Long userId, Integer messageId) {
        showDeleteCoursesPage(userId, messageId, 0);
    }

    public void promptCurrentImage(Long userId, Integer messageId) {
        requestNextImage(userId, messageId);
    }

    public void showEditCoursesPage(Long userId, Integer messageId, int page) {
        var result = navigationService.getAllCoursesPage(page);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_EDIT, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_EDIT_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboardForAdmin(result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_EDIT);
        editMessage(userId, messageId, text, keyboard);
    }

    private void showDeleteCoursesPage(Long userId, Integer messageId, int page) {
        var result = navigationService.getAllCoursesPage(page);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_DELETE, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_DELETE_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildCoursesKeyboardForAdmin(result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_EDIT);
        editMessage(userId, messageId, text, keyboard);
    }

    private void handleSelectCourseForEditInternal(Long userId, Integer messageId, Long courseId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingCourseId(courseId);
        sessionService.updateSessionContext(userId, context);

        String text = MSG_WHAT_TO_CHANGE;
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_NAME_DESC).callbackData("edit_course_action:name_desc"),
                        new InlineKeyboardButton(BUTTON_SECTIONS).callbackData("edit_course_action:sections")
                }
        );
        keyboard.addRow(new InlineKeyboardButton(BUTTON_BACK).callbackData(CALLBACK_EDIT_COURSE));
        editMessage(userId, messageId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_CHOOSE_ACTION);
    }

    private void handleSelectCourseForDeleteInternal(Long userId, Integer messageId, Long courseId) {
        String text = MSG_CONFIRM_DELETE_COURSE;
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(BUTTON_YES_DELETE).callbackData("confirm_delete_course:" + courseId),
                new InlineKeyboardButton(BUTTON_NO).callbackData(CALLBACK_EDIT_COURSE)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    private void handleEditCourseActionInternal(Long userId, Integer messageId, String action) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_COURSE_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_NAME_DESC);
        } else if (ACTION_SECTIONS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            var result = navigationService.getSectionsPage(courseId, 0);
            String text = MSG_SELECT_SECTION;
            // Добавлен четвёртый аргумент CALLBACK_EDIT_COURSE
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboardForAdmin(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        }
    }

    private void handleSelectSectionForEditInternal(Long userId, Integer messageId, Long sectionId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingSectionId(sectionId);
        sessionService.updateSessionContext(userId, context);

        String text = "Что хотите изменить в разделе?";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_NAME_DESC).callbackData("edit_section_action:name_desc"),
                        new InlineKeyboardButton(BUTTON_TOPICS).callbackData("edit_section_action:topics")
                }
        );
        keyboard.addRow(new InlineKeyboardButton(BUTTON_BACK).callbackData(CALLBACK_ADMIN_BACK_TO_SECTIONS));
        editMessage(userId, messageId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
    }

    private void handleEditSectionActionInternal(Long userId, Integer messageId, String action) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_SECTION_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
        } else if (ACTION_TOPICS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            var result = navigationService.getTopicsPage(sectionId, 0);
            String text = MSG_SELECT_TOPIC;
            // Добавлен четвёртый аргумент CALLBACK_ADMIN_BACK_TO_SECTIONS
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildTopicsKeyboardForAdmin(
                    result, sectionId, CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
        }
    }

    private void handleSelectTopicForEditInternal(Long userId, Integer messageId, Long topicId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingTopicId(topicId);
        sessionService.updateSessionContext(userId, context);

        editMessage(userId, messageId,
                MSG_SEND_JSON_TOPIC,
                createAdminCancelKeyboardWithBackToTopics());
        sessionService.updateSessionState(userId, BotState.EDIT_TOPIC_JSON);
    }

    private void handleConfirmDeleteCourseInternal(Long userId, Integer messageId, Long courseId) {
        try {
            userProgressRepository.deleteByCourseId(courseId);
            courseRepository.deleteById(courseId);
            editMessage(userId, messageId, MSG_COURSE_DELETED, createBackToMainKeyboard());
        } catch (Exception e) {
            log.error("Error deleting course", e);
            editMessage(userId, messageId, MSG_ERROR_DELETING_COURSE, createBackToMainKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    private void handleCourseJson(Long userId, Message message) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            var document = message.document();
            String fileId = document.fileId();
            var file = telegramBot.execute(new GetFile(fileId)).file();
            byte[] fileContent = telegramBot.getFileContent(file);
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
            if (progressMessageId != null) {
                deleteMessage(userId, progressMessageId);
            }
        }
    }

    private void handleCourseNameDescJsonInternal(Long userId, Message message) {
        var document = message.document();
        String fileId = document.fileId();
        try {
            var file = telegramBot.execute(new GetFile(fileId)).file();
            byte[] fileContent = telegramBot.getFileContent(file);
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

            String response = String.format(
                    FORMAT_COURSE_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription()
            );
            sendMessage(userId, response, createBackToMainKeyboard());
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);

        } catch (Exception e) {
            log.error("Error updating course name/desc from JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void handleSectionNameDescJsonInternal(Long userId, Message message) {
        var document = message.document();
        String fileId = document.fileId();
        try {
            var file = telegramBot.execute(new GetFile(fileId)).file();
            byte[] fileContent = telegramBot.getFileContent(file);
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

            String response = String.format(
                    FORMAT_SECTION_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription()
            );
            sendMessage(userId, response);

            context.setEditingCourseId(courseId);
            sessionService.updateSessionContext(userId, context);

            var result = navigationService.getSectionsPage(courseId, 0);
            String text = MSG_SELECT_SECTION;
            InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboardForAdmin(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            sendMessage(userId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);

        } catch (Exception e) {
            log.error("Error updating section name/desc from JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void handleTopicJsonInternal(Long userId, Message message) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            var document = message.document();
            String fileId = document.fileId();
            var file = telegramBot.execute(new GetFile(fileId)).file();
            byte[] fileContent = telegramBot.getFileContent(file);
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
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            Integer page = context.getAdminTopicsPage(); // текущая страница тем
            String backCallback = CALLBACK_ADMIN_BACK_TO_TOPICS + ":" + sectionId + ":" + page;
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_RETRY).callbackData(CALLBACK_RETRY)},
                    new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_CANCEL).callbackData(backCallback)}
            );
            sendMessage(userId, e.getMessage(), keyboard);
        } catch (Exception e) {
            log.error("Error importing topic from JSON", e);
            // Здесь тоже можно использовать ту же логику, но пока оставим общую ошибку
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) {
                deleteMessage(userId, progressMessageId);
            }
        }
    }

    private void handleImageUploadInternal(Long userId, Message message) {
        if (message.photo() == null || message.photo().length == 0) {
            sendMessage(userId, MSG_PLEASE_SEND_PHOTO, createRetryOrCancelKeyboard());
            return;
        }
        var photo = message.photo()[message.photo().length - 1];
        String fileId = photo.fileId();

        UserContext context = sessionService.getCurrentContext(userId);
        Long entityId = context.getTargetEntityId();
        String entityType = context.getTargetEntityType();

        try {
            var file = telegramBot.execute(new GetFile(fileId)).file();
            byte[] fileContent = telegramBot.getFileContent(file);
            InputStream inputStream = new ByteArrayInputStream(fileContent);

            String fileName = System.currentTimeMillis() + "_" + fileId + ".jpg";
            Path targetPath = Paths.get("uploads", fileName);
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            if (ENTITY_BLOCK.equals(entityType)) {
                BlockImage blockImage = blockImageRepository.findById(entityId).orElseThrow();
                blockImage.setFilePath(targetPath.toAbsolutePath().toString());
                blockImageRepository.save(blockImage);
            } else if (ENTITY_QUESTION.equals(entityType)) {
                QuestionImage questionImage = questionImageRepository.findById(entityId).orElseThrow();
                questionImage.setFilePath(targetPath.toAbsolutePath().toString());
                questionImageRepository.save(questionImage);
            }

            context.setCurrentImageIndex(context.getCurrentImageIndex() + 1);
            sessionService.updateSessionContext(userId, context);
            requestNextImage(userId, message.messageId());

        } catch (Exception e) {
            log.error("Error saving image", e);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createRetryOrCancelKeyboard());
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
                showEditTopicsPage(userId, null, sectionId, 0);
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
                showEditTopicsPage(userId, null, sectionId, 0);
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
    public void showEditCourseSectionsPage(Long userId, Integer messageId, Long courseId, int page) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminSectionsPage(page);
        sessionService.updateSessionContext(userId, context);

        var result = navigationService.getSectionsPage(courseId, page);
        String courseTitle = navigationService.getCourseTitle(courseId);
        String text = String.format(FORMAT_EDIT_SECTIONS_HEADER,
                courseTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        // backCallback указывает на возврат к списку курсов
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildSectionsKeyboardForAdmin(result, courseId,
                CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }
    public void showEditTopicsPage(Long userId, Integer messageId, Long sectionId, int page) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminTopicsPage(page);
        sessionService.updateSessionContext(userId, context);

        var result = navigationService.getTopicsPage(sectionId, page);
        String sectionTitle = navigationService.getSectionTitle(sectionId);
        String text = String.format(FORMAT_EDIT_TOPICS_HEADER,
                sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        // backCallback ведёт на admin_back_to_sections
        InlineKeyboardMarkup keyboard = keyboardBuilder.buildTopicsKeyboardForAdmin(result, sectionId,
                CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    // ================== Вспомогательные клавиатуры и сообщения ==================

    public void handleBackToCoursesFromEdit(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long editingCourseId = context.getEditingCourseId();
        if (editingCourseId != null) {
            // возвращаемся к редактированию разделов этого курса?
            // по логике текущего кода – показываем список курсов для редактирования
            showEditCoursesPage(userId, messageId, 0);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToSectionsFromEdit(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long courseId = context.getEditingCourseId();
        if (courseId != null) {
            Integer page = context.getAdminSectionsPage();
            if (page == null) page = 0;
            showEditCourseSectionsPage(userId, messageId, courseId, page);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long sectionId = context.getEditingSectionId();
        if (sectionId != null) {
            Integer page = context.getAdminTopicsPage();
            if (page == null) page = 0;
            showEditTopicsPage(userId, messageId, sectionId, page);
        } else {
            sendMainMenu(userId, messageId);
        }
    }
    public boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
    }

    public void handleAdminCoursesPage(Long userId, Integer messageId, String source, int page) {
        showEditCoursesPage(userId, messageId, page);
    }

    public void handleAdminSectionsPage(Long userId, Integer messageId, Long courseId, int page) {
        showEditCourseSectionsPage(userId, messageId, courseId, page);
    }

    public void handleAdminTopicsPage(Long userId, Integer messageId, Long sectionId, int page) {
        showEditTopicsPage(userId, messageId, sectionId, page);
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, Long sectionId, int page) {
        showEditTopicsPage(userId, messageId, sectionId, page);
    }
}