package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public class TestHandler extends BaseHandler {

    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserTestResultRepository userTestResultRepository;
    private final CourseNavigationHandler courseNavHandler;

    // Блокировки для синхронизации операций с одним пользователем
    private final ConcurrentMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    public TestHandler(MessageSender messageSender,
                       UserSessionService sessionService,
                       NavigationService navigationService,
                       QuestionRepository questionRepository,
                       AdminUserRepository adminUserRepository,
                       AnswerOptionRepository answerOptionRepository,
                       UserProgressRepository userProgressRepository,
                       UserMistakeRepository userMistakeRepository,
                       UserTestResultRepository userTestResultRepository,
                       CourseNavigationHandler courseNavHandler,
                       UserSettingsService userSettingsService) {
        super(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService);
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.userProgressRepository = userProgressRepository;
        this.userMistakeRepository = userMistakeRepository;
        this.userTestResultRepository = userTestResultRepository;
        this.courseNavHandler = courseNavHandler;
    }

    private Object getLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    // ================== Публичные методы для диспетчера ==================
    public void handleTestTopic(Long userId, Integer messageId, Long topicId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            log.info("handleTestTopic START: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            context.setPreviousTopicPage(context.getCurrentPage());
            sessionService.updateSessionContext(userId, context);

            // Принудительный сброс старых тестовых данных
            context.setTestMode(false);
            context.setTestQuestionIds(new ArrayList<>());
            context.setTestType(null);
            context.setCurrentTestQuestionIndex(0);
            context.setCorrectAnswers(0);
            context.setWrongAnswers(0);
            sessionService.updateSessionContext(userId, context);
            log.info("handleTestTopic: cleared old test context");

            // Принудительно перечитываем контекст, чтобы убедиться, что сброс применился
            context = sessionService.getCurrentContext(userId);

            List<Question> questions = navigationService.getAllQuestionsForTopic(topicId);
            log.info("handleTestTopic: topicId={}, questions.size={}", topicId, questions.size());

            if (questions.isEmpty()) {
                String text = MSG_TOPIC_NO_QUESTIONS;
                if (messageId != null) {
                    editMessage(userId, messageId, text, createBackToMainKeyboard());
                } else {
                    sendMessage(userId, text, createBackToMainKeyboard());
                }
                return;
            }

            questions = new ArrayList<>(questions);
            Collections.shuffle(questions);
            initTestContext(context, TEST_TYPE_TOPIC, questions);
            context.setCurrentTopicId(topicId);

            log.info("After initTestContext: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            sessionService.updateSession(userId, BotState.QUESTION, context);

            // Принудительно перечитываем контекст после сохранения
            context = sessionService.getCurrentContext(userId);
            log.info("After updateSession: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                    .ifPresent(question -> showTestQuestion(userId, messageId, question));
        }
    }

    public void handleTestSection(Long userId, Integer messageId, Long sectionId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            context.setPreviousSectionPage(context.getCurrentPage());
            sessionService.updateSessionContext(userId, context);

            UserSettings settings = userSettingsService.getSettings(userId);
            int questionsPerBlock = settings.getTestQuestionsPerBlock();
            List<Question> questions = navigationService.getRandomQuestionsForSection(sectionId, questionsPerBlock);

            if (questions.isEmpty()) {
                String text = MSG_SECTION_NO_QUESTIONS;
                if (messageId != null) {
                    editMessage(userId, messageId, text, createBackToMainKeyboard());
                } else {
                    sendMessage(userId, text, createBackToMainKeyboard());
                }
                return;
            }

            questions = new ArrayList<>(questions);
            Collections.shuffle(questions);
            initTestContext(context, TEST_TYPE_SECTION, questions);
            context.setCurrentSectionId(sectionId);
            sessionService.updateSession(userId, BotState.QUESTION, context);

            context = sessionService.getCurrentContext(userId);
            log.info("After updateSession: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                    .ifPresent(question -> showTestQuestion(userId, messageId, question));
        }
    }

    public void handleTestCourse(Long userId, Integer messageId, Long courseId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            context.setPreviousCoursesPage(context.getCurrentPage());
            sessionService.updateSessionContext(userId, context);

            UserSettings settings = userSettingsService.getSettings(userId);
            int questionsPerTopic = settings.getTestQuestionsPerBlock();
            List<Question> questions = navigationService.getRandomQuestionsForCourse(courseId, questionsPerTopic);

            if (questions.isEmpty()) {
                String text = MSG_COURSE_NO_QUESTIONS;
                if (messageId != null) {
                    editMessage(userId, messageId, text, createBackToMainKeyboard());
                } else {
                    sendMessage(userId, text, createBackToMainKeyboard());
                }
                return;
            }

            questions = new ArrayList<>(questions);
            Collections.shuffle(questions);
            initTestContext(context, TEST_TYPE_COURSE, questions);
            context.setCurrentCourseId(courseId);
            context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
            sessionService.updateSession(userId, BotState.QUESTION, context);

            context = sessionService.getCurrentContext(userId);
            log.info("After updateSession: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                    .ifPresent(question -> showTestQuestion(userId, messageId, question));
        }
    }

    public void handleMyMistakes(Long userId, Integer messageId) {
        synchronized (getLock(userId)) {
            List<Question> questions = navigationService.getMistakeQuestions(userId);
            if (questions.isEmpty()) {
                String text = MSG_NO_MISTAKES;
                if (messageId != null) {
                    editMessage(userId, messageId, text, createBackToMainKeyboard());
                } else {
                    sendMessage(userId, text, createBackToMainKeyboard());
                }
                return;
            }

            Collections.shuffle(questions);
            UserContext context = sessionService.getCurrentContext(userId);
            initTestContext(context, TEST_TYPE_MISTAKE, questions);
            sessionService.updateSession(userId, BotState.QUESTION, context);

            context = sessionService.getCurrentContext(userId);
            log.info("After updateSession: correct={}, wrong={}, testQuestionIds.size={}, currentIndex={}",
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getTestQuestionIds().size(), context.getCurrentTestQuestionIndex());

            navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                    .ifPresent(question -> showTestQuestion(userId, messageId, question));
        }
    }

    private void initTestContext(UserContext context, String testType, List<Question> questions) {
        context.setTestMode(true);
        context.setTestType(testType);
        context.setTestQuestionIds(questions.stream().map(Question::getId).toList());
        context.setCurrentTestQuestionIndex(0);
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);
        log.info("initTestContext: testType={}, questions.size={}", testType, questions.size());
        for (Question q : questions) {
            log.debug("initTestContext: question id={}", q.getId());
        }
    }

    private void updateAfterAnswer(Long userId, Long questionId, boolean correct, UserContext context) {
        boolean isLearning = !context.isTestMode();
        navigationService.saveAnswerProgress(userId, questionId, correct, isLearning);

        if (correct) {
            navigationService.clearMistake(userId, questionId);
            context.setCorrectAnswers(context.getCorrectAnswers() + 1);
        } else {
            navigationService.recordMistake(userId, questionId);
            context.setWrongAnswers(context.getWrongAnswers() + 1);
        }
        sessionService.updateSessionContext(userId, context);
    }

    private boolean isLastInCurrentMode(UserContext context) {
        if (context.isTestMode()) {
            List<Long> questionIds = context.getTestQuestionIds();
            int currentIdx = context.getCurrentTestQuestionIndex();
            return currentIdx == questionIds.size() - 1;
        } else {
            List<Long> blockIds = context.getCurrentTopicBlockIds();
            if (blockIds == null || blockIds.isEmpty()) {
                log.warn("isLastInCurrentMode: currentTopicBlockIds is empty in non-test mode, treating as last");
                return true;
            }
            Long currentBlockId = blockIds.get(context.getCurrentBlockIndex());
            List<Question> blockQuestions = navigationService.getQuestionsForBlock(currentBlockId);
            int currentQIdx = context.getCurrentBlockQuestionIndex();
            return currentQIdx == blockQuestions.size() - 1;
        }
    }

    public void handleAnswer(Long userId, Integer messageId, Long questionId, Long answerOptionId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            log.info("handleAnswer START: testMode={}, testQuestionIds.size={}, currentIndex={}, correct={}, wrong={}",
                    context.isTestMode(), context.getTestQuestionIds().size(),
                    context.getCurrentTestQuestionIndex(), context.getCorrectAnswers(), context.getWrongAnswers());
            log.info("handleAnswer: userId={}, testMode={}, questionId={}, answerOptionId={}, currentIndex={}, totalQuestions={}",
                    userId, context.isTestMode(), questionId, answerOptionId,
                    context.getCurrentTestQuestionIndex(), context.getTestQuestionIds().size());

            // Guard: защита от повреждённого контекста сессии.
            // Возможная причина: сессия была сброшена при слиянии аккаунтов — пользователь должен начать тест заново.
            if (context.isTestMode() && context.getTestQuestionIds().isEmpty()) {
                log.warn("handleAnswer: testMode=true but testQuestionIds empty for user {} — session was reset, asking to retry", userId);
                sendStaleContextMessage(userId, messageId);
                return;
            }
            if (!context.isTestMode() && (context.getCurrentTopicBlockIds() == null || context.getCurrentTopicBlockIds().isEmpty())) {
                log.warn("handleAnswer: testMode=false but currentTopicBlockIds empty for user {} — session was reset, asking to retry", userId);
                sendStaleContextMessage(userId, messageId);
                return;
            }

            AnswerOption selected = processAnswerSelection(questionId, answerOptionId);
            if (selected == null) {
                sendErrorMessage(userId, messageId);
                return;
            }

            boolean correct = selected.getIsCorrect();
            updateAfterAnswer(userId, questionId, correct, context);

            boolean isLast = isLastInCurrentMode(context);

            if (context.isTestMode()) {
                if (correct) {
                    if (isLast) {
                        showTestSummary(userId, messageId);
                    } else {
                        handleNextQuestion(userId, messageId);
                    }
                } else {
                    String resultText = buildResultText(userId, context, correct, isLast);
                    BotKeyboard keyboard = buildResultKeyboardAfterWrongBot(context, isLast);
                    sendOrEditResult(userId, messageId, resultText, keyboard);
                }
            } else {
                String resultText = buildResultText(userId, context, correct, isLast);
                BotKeyboard keyboard = buildResultKeyboardBot(context, isLast);
                sendOrEditResult(userId, messageId, resultText, keyboard);
            }

            if (context.getCurrentTopicId() != null && !context.isTestMode()) {
                navigationService.recordStudyAction(userId, context.getCurrentTopicId());
            }
        }
    }

    private String buildResultText(Long userId, UserContext context, boolean correct, boolean isLast) {
        UserSettings settings = userSettingsService.getSettings(userId);
        if (context.isTestMode() && isLast && correct) {
            return String.format(FORMAT_TEST_COMPLETED,
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getCorrectAnswers() + context.getWrongAnswers());
        } else if (context.isTestMode() && isLast && !correct) {
            String resultText = MSG_WRONG;
            if (settings.getShowExplanations()) {
                resultText += "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
            }
            return resultText;
        } else if (context.isTestMode()) {
            String resultText = correct ? MSG_CORRECT : MSG_WRONG;
            if (!correct && settings.getShowExplanations()) {
                resultText += "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
            }
            return resultText;
        } else {
            String resultText = correct ? MSG_CORRECT : MSG_WRONG;
            if (settings.getShowExplanations()) {
                resultText += "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
            }
            return resultText;
        }
    }

    private void showTestSummary(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        saveTestResultIfNeeded(userId, context);
        String stats = String.format(FORMAT_TEST_COMPLETED,
                context.getCorrectAnswers(), context.getWrongAnswers(),
                context.getCorrectAnswers() + context.getWrongAnswers());
        String backCallbackData = getBackCallbackData(context);
        BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_COMPLETE, backCallbackData));
        sendOrEditResult(userId, messageId, stats, keyboard);
    }

    private BotKeyboard buildResultKeyboardBot(UserContext context, boolean isLast) {
        if (context.isTestMode()) {
            if (isLast) {
                return new BotKeyboard().addRow(BotButton.callback(BUTTON_COMPLETE, getBackCallbackData(context)));
            } else {
                return new BotKeyboard().addRow(BotButton.callback(BUTTON_NEXT, CALLBACK_NEXT_QUESTION));
            }
        } else {
            if (isLast) {
                return new BotKeyboard()
                        .addRow(BotButton.callback(BUTTON_NEXT, CALLBACK_NEXT_BLOCK))
                        .addRow(BotButton.callback(BUTTON_BACK_TO_TEXT, CALLBACK_BACK_TO_BLOCK_TEXT))
                        .addRow(BotButton.callback(BUTTON_EXIT_TOPIC, CALLBACK_BACK_TO_TOPICS));
            } else {
                return new BotKeyboard()
                        .addRow(BotButton.callback(BUTTON_NEXT, CALLBACK_NEXT_QUESTION))
                        .addRow(BotButton.callback(BUTTON_BACK_TO_TEXT, CALLBACK_PREV_QUESTION))
                        .addRow(BotButton.callback(BUTTON_EXIT_TOPIC, CALLBACK_BACK_TO_TOPICS));
            }
        }
    }

    private String getBackCallbackData(UserContext context) {
        String testType = context.getTestType();
        if (TEST_TYPE_MISTAKE.equals(testType)) {
            return CALLBACK_MAIN_MENU;
        } else if (TEST_TYPE_SECTION.equals(testType)) {
            return CALLBACK_BACK_TO_SECTIONS;
        } else if (TEST_TYPE_COURSE.equals(testType)) {
            return CALLBACK_BACK_TO_COURSES;
        } else {
            return CALLBACK_BACK_TO_TOPICS;
        }
    }

    private void sendOrEditResult(Long userId, Integer messageId, String text, BotKeyboard keyboard) {
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private void sendErrorMessage(Long userId, Integer messageId) {
        if (messageId != null) {
            editMessage(userId, messageId, MSG_WRONG_OPTION, createBackToMainKeyboard());
        } else {
            sendMessage(userId, MSG_WRONG_OPTION, createBackToMainKeyboard());
        }
    }

    private void sendStaleContextMessage(Long userId, Integer messageId) {
        String text = "⚠️ Сессия устарела (возможно, был сброс после связывания аккаунтов). Пожалуйста, вернитесь к теме и начните тест заново.";
        if (messageId != null) {
            editMessage(userId, messageId, text, createBackToMainKeyboard());
        } else {
            sendMessage(userId, text, createBackToMainKeyboard());
        }
    }

    private String getExplanationForCurrentQuestion(UserContext context) {
        Long questionId = getCurrentQuestionId(context);
        return navigationService.getQuestion(questionId)
                .map(Question::getExplanation)
                .orElse("");
    }

    private Long getCurrentQuestionId(UserContext context) {
        if (context.isTestMode()) {
            return context.getTestQuestionIds().get(context.getCurrentTestQuestionIndex());
        } else {
            return context.getCurrentBlockQuestionIds().get(context.getCurrentBlockQuestionIndex());
        }
    }

    private AnswerOption processAnswerSelection(Long questionId, Long answerOptionId) {
        List<AnswerOption> options = navigationService.getAnswerOptionsForQuestion(questionId);
        return options.stream()
                .filter(opt -> opt.getId().equals(answerOptionId))
                .findFirst()
                .orElse(null);
    }

    private BotKeyboard buildResultKeyboardAfterWrongBot(UserContext context, boolean isLast) {
        return new BotKeyboard().addRow(BotButton.callback(BUTTON_NEXT, CALLBACK_NEXT_QUESTION));
    }

    private void saveTestResultIfNeeded(Long userId, UserContext context) {
        Long testId = null;
        String testType = context.getTestType();
        if (TEST_TYPE_TOPIC.equals(testType)) {
            testId = context.getCurrentTopicId();
        } else if (TEST_TYPE_SECTION.equals(testType)) {
            testId = context.getCurrentSectionId();
        } else if (TEST_TYPE_COURSE.equals(testType)) {
            testId = context.getCurrentCourseId();
        }
        if (testId != null && !TEST_TYPE_MISTAKE.equals(testType)) {
            navigationService.saveTestResult(userId, testType, testId,
                    context.getCorrectAnswers(), context.getWrongAnswers());
        }
    }

    public void handleNextQuestion(Long userId, Integer messageId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            if (context.isTestMode()) {
                List<Long> questionIds = context.getTestQuestionIds();
                int currentIdx = context.getCurrentTestQuestionIndex();
                if (currentIdx + 1 < questionIds.size()) {
                    context.setCurrentTestQuestionIndex(currentIdx + 1);
                    sessionService.updateSessionContext(userId, context);
                    navigationService.getQuestionWithImagesAndOptions(questionIds.get(currentIdx + 1))
                            .ifPresent(question -> showTestQuestion(userId, messageId, question));
                } else {
                    showTestSummary(userId, messageId);
                }
            } else {
                Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
                List<Question> questions = navigationService.getQuestionsForBlock(currentBlockId);
                if (context.getCurrentBlockQuestionIndex() == -1) {
                    if (!questions.isEmpty()) {
                        context.setCurrentBlockQuestionIndex(0);
                        sessionService.updateSessionContext(userId, context);
                        navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                                .ifPresent(question -> showTestQuestion(userId, messageId, question));
                    } else {
                        courseNavHandler.handleNextBlock(userId, messageId);
                    }
                } else {
                    int nextIdx = context.getCurrentBlockQuestionIndex() + 1;
                    if (nextIdx < questions.size()) {
                        context.setCurrentBlockQuestionIndex(nextIdx);
                        sessionService.updateSessionContext(userId, context);
                        navigationService.getQuestionWithImagesAndOptions(questions.get(nextIdx).getId())
                                .ifPresent(question -> showTestQuestion(userId, messageId, question));
                    } else {
                        courseNavHandler.handleNextBlock(userId, messageId);
                    }
                }
            }
        }
    }

    public void handlePrevQuestion(Long userId, Integer messageId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            if (context.isTestMode()) {
                List<Long> questionIds = context.getTestQuestionIds();
                int currentIdx = context.getCurrentTestQuestionIndex();
                if (currentIdx - 1 >= 0) {
                    context.setCurrentTestQuestionIndex(currentIdx - 1);
                    sessionService.updateSessionContext(userId, context);
                    navigationService.getQuestionWithImagesAndOptions(questionIds.get(currentIdx - 1))
                            .ifPresent(question -> showTestQuestion(userId, messageId, question));
                } else {
                    String testType = context.getTestType();
                    if (TEST_TYPE_SECTION.equals(testType)) {
                        courseNavHandler.handleBackToSections(userId, messageId);
                    } else if (TEST_TYPE_COURSE.equals(testType)) {
                        courseNavHandler.handleBackToCourses(userId, messageId);
                    } else {
                        courseNavHandler.handleBackToTopics(userId, messageId);
                    }
                }
            } else {
                Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
                List<Question> questions = navigationService.getQuestionsForBlock(currentBlockId);
                if (context.getCurrentBlockQuestionIndex() == -1) {
                    courseNavHandler.handlePrevBlock(userId, messageId);
                } else {
                    int prevIdx = context.getCurrentBlockQuestionIndex() - 1;
                    if (prevIdx >= 0) {
                        context.setCurrentBlockQuestionIndex(prevIdx);
                        sessionService.updateSessionContext(userId, context);
                        navigationService.getQuestionWithImagesAndOptions(questions.get(prevIdx).getId())
                                .ifPresent(question -> showTestQuestion(userId, messageId, question));
                    } else {
                        context.setCurrentBlockQuestionIndex(-1);
                        sessionService.updateSessionContext(userId, context);
                        courseNavHandler.showBlockContent(userId, messageId, currentBlockId);
                    }
                }
            }
        }
    }

    public void handleBackToBlockText(Long userId, Integer messageId) {
        synchronized (getLock(userId)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
            context.setCurrentBlockQuestionIndex(-1);
            sessionService.updateSessionContext(userId, context);
            courseNavHandler.showBlockContent(userId, messageId, currentBlockId);
        }
    }

    // ================== Внутренние методы ==================
    private void showTestQuestion(Long userId, Integer messageId, Question question) {
        // Этот метод вызывается только из синхронизированных методов, поэтому дополнительная блокировка не нужна
        UserContext context = sessionService.getCurrentContext(userId);
        String backCallbackData;
        int currentNumber;
        int totalQuestions;

        if (context.isTestMode()) {
            currentNumber = context.getCurrentTestQuestionIndex() + 1;
            totalQuestions = context.getTestQuestionIds().size();

            if (currentNumber == 1) {
                backCallbackData = getBackCallbackData(context);
            } else {
                backCallbackData = CALLBACK_PREV_QUESTION;
            }
        } else {
            backCallbackData = CALLBACK_PREV_QUESTION;
            currentNumber = context.getCurrentBlockQuestionIndex() + 1;
            totalQuestions = context.getCurrentBlockQuestionIds().size();
        }

        sendMediaGroup(userId, question.getImages());

        String text = String.format(FORMAT_QUESTION, currentNumber, totalQuestions, question.getText());
        UserSettings settings = userSettingsService.getSettings(userId);
        List<AnswerOption> options = new ArrayList<>(navigationService.getAnswerOptionsForQuestion(question.getId()));
        if (settings.getShuffleOptions()) {
            Collections.shuffle(options);
        }
        BotKeyboard keyboard = buildAnswerOptionsKeyboardBot(options, question.getId(), backCallbackData);

        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private BotKeyboard buildAnswerOptionsKeyboardBot(List<AnswerOption> options, Long questionId, String backCallbackData) {
        BotKeyboard keyboard = new BotKeyboard();
        for (AnswerOption opt : options) {
            keyboard.addRow(BotButton.callback(opt.getText(), "answer:" + questionId + ":" + opt.getId()));
        }
        keyboard.addRow(BotButton.callback(BUTTON_BACK, backCallbackData));
        return keyboard;
    }
}