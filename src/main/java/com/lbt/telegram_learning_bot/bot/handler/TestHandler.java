package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
public class TestHandler extends BaseHandler {

    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserTestResultRepository userTestResultRepository;
    private final CourseNavigationHandler courseNavHandler;

    public TestHandler(TelegramBot telegramBot,
                       UserSessionService sessionService,
                       NavigationService navigationService,
                       QuestionRepository questionRepository,
                       AdminUserRepository adminUserRepository,
                       AnswerOptionRepository answerOptionRepository,
                       UserProgressRepository userProgressRepository,
                       UserMistakeRepository userMistakeRepository,
                       UserTestResultRepository userTestResultRepository,
                       CourseNavigationHandler courseNavHandler) { // добавить
        super(telegramBot, sessionService, navigationService, adminUserRepository);
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.userProgressRepository = userProgressRepository;
        this.userMistakeRepository = userMistakeRepository;
        this.userTestResultRepository = userTestResultRepository;
        this.courseNavHandler = courseNavHandler; // добавить
    }
    // ================== Публичные методы для диспетчера ==================
    public void handleTestTopic(Long userId, Integer messageId, Long topicId) {
        List<Question> questions = navigationService.getAllQuestionsForTopic(topicId);
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
        UserContext context = sessionService.getCurrentContext(userId);
        context.setTestMode(true);
        context.setTestType(TEST_TYPE_TOPIC);
        context.setTestQuestionIds(questions.stream().map(Question::getId).toList());
        context.setCurrentTestQuestionIndex(0);
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);
        context.setCurrentTopicId(topicId);
        sessionService.updateSession(userId, BotState.QUESTION, context);
        navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                .ifPresent(question -> showTestQuestion(userId, messageId, question));
    }

    public void handleTestSection(Long userId, Integer messageId, Long sectionId) {
        List<Question> questions = navigationService.getRandomQuestionsForSection(sectionId, 2);
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
        UserContext context = sessionService.getCurrentContext(userId);
        context.setTestMode(true);
        context.setTestType(TEST_TYPE_SECTION);
        context.setTestQuestionIds(questions.stream().map(Question::getId).toList());
        context.setCurrentTestQuestionIndex(0);
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);
        context.setCurrentSectionId(sectionId);
        sessionService.updateSession(userId, BotState.QUESTION, context);
        navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                .ifPresent(question -> showTestQuestion(userId, messageId, question));
    }

    public void handleTestCourse(Long userId, Integer messageId, Long courseId) {
        List<Question> questions = navigationService.getRandomQuestionsForCourse(courseId, 2);
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
        UserContext context = sessionService.getCurrentContext(userId);
        context.setTestMode(true);
        context.setTestType(TEST_TYPE_COURSE);
        context.setTestQuestionIds(questions.stream().map(Question::getId).toList());
        context.setCurrentTestQuestionIndex(0);
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);
        context.setCurrentCourseId(courseId);
        context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
        sessionService.updateSession(userId, BotState.QUESTION, context);
        navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                .ifPresent(question -> showTestQuestion(userId, messageId, question));
    }

    public void handleMyMistakes(Long userId, Integer messageId) {
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
        context.setTestMode(true);
        context.setTestType(TEST_TYPE_MISTAKE);
        context.setTestQuestionIds(questions.stream().map(Question::getId).toList());
        context.setCurrentTestQuestionIndex(0);
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);
        sessionService.updateSession(userId, BotState.QUESTION, context);
        navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                .ifPresent(question -> showTestQuestion(userId, messageId, question));
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
            Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
            List<Question> blockQuestions = navigationService.getQuestionsForBlock(currentBlockId);
            int currentQIdx = context.getCurrentBlockQuestionIndex();
            return currentQIdx == blockQuestions.size() - 1;
        }
    }
    public void handleAnswer(Long userId, Integer messageId, Long questionId, Long answerOptionId) {
        UserContext context = sessionService.getCurrentContext(userId);

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
                    // Правильный ответ на последний вопрос – сразу статистика
                    showTestSummary(userId, messageId);
                } else {
                    // Правильный ответ – сразу следующий вопрос
                    handleNextQuestion(userId, messageId);
                }
            } else {
                // Неправильный ответ – показываем пояснение (с кнопкой "Далее")
                String resultText = buildResultText(context, correct, isLast);
                InlineKeyboardMarkup keyboard = buildResultKeyboardAfterWrong(context, isLast);
                sendOrEditResult(userId, messageId, resultText, keyboard);
            }
        } else {
            // Учебный режим – без изменений
            String resultText = buildResultText(context, correct, isLast);
            InlineKeyboardMarkup keyboard = buildResultKeyboard(context, isLast);
            sendOrEditResult(userId, messageId, resultText, keyboard);
        }

        if (context.getCurrentTopicId() != null && !context.isTestMode()) {
            navigationService.recordStudyAction(userId, context.getCurrentTopicId());
        }
    }

    private String buildResultText(UserContext context, boolean correct, boolean isLast) {
        if (context.isTestMode() && isLast && correct) {
            // Правильный ответ на последний вопрос – статистика
            return String.format(FORMAT_TEST_COMPLETED,
                    context.getCorrectAnswers(), context.getWrongAnswers(),
                    context.getCorrectAnswers() + context.getWrongAnswers());
        } else if (context.isTestMode() && isLast && !correct) {
            // Неправильный ответ на последний вопрос – показываем пояснение
            String resultText = MSG_WRONG;
            return resultText + "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
        } else if (context.isTestMode()) {
            // Тестовый режим, не последний вопрос
            String resultText = correct ? MSG_CORRECT : MSG_WRONG;
            if (correct) {
                return resultText; // без пояснения
            } else {
                return resultText + "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
            }
        } else {
            // Учебный режим
            String resultText = correct ? MSG_CORRECT : MSG_WRONG;
            return resultText + "\n\nПояснение: " + getExplanationForCurrentQuestion(context);
        }
    }
    private void showTestSummary(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        saveTestResultIfNeeded(userId, context);
        String stats = String.format(FORMAT_TEST_COMPLETED,
                context.getCorrectAnswers(), context.getWrongAnswers(),
                context.getCorrectAnswers() + context.getWrongAnswers());
        String backCallbackData = getBackCallbackData(context);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_COMPLETE).callbackData(backCallbackData)}
        );
        sendOrEditResult(userId, messageId, stats, keyboard);
    }
    private InlineKeyboardMarkup buildResultKeyboard(UserContext context, boolean isLast) {
        if (context.isTestMode()) {
            if (isLast) {
                return new InlineKeyboardMarkup(
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_COMPLETE).callbackData(getBackCallbackData(context))}
                );
            } else {
                return new InlineKeyboardMarkup(
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_NEXT).callbackData(CALLBACK_NEXT_QUESTION)}
                );
            }
        } else {
            if (isLast) {
                return new InlineKeyboardMarkup(
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_NEXT).callbackData(CALLBACK_NEXT_BLOCK)},
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_BACK_TO_TEXT).callbackData(CALLBACK_BACK_TO_BLOCK_TEXT)}
                ).addRow(new InlineKeyboardButton(BUTTON_EXIT_TOPIC).callbackData(CALLBACK_BACK_TO_TOPICS));
            } else {
                return new InlineKeyboardMarkup(
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_NEXT).callbackData(CALLBACK_NEXT_QUESTION)},
                        new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_BACK_TO_TEXT).callbackData(CALLBACK_PREV_QUESTION)}
                ).addRow(new InlineKeyboardButton(BUTTON_EXIT_TOPIC).callbackData(CALLBACK_BACK_TO_TOPICS));
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
    private void sendOrEditResult(Long userId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
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
    private String getExplanationForCurrentQuestion(UserContext context) {
        // Получаем текущий вопрос (нужно либо передавать question, либо доставать из БД)
        // Здесь проще передавать Question как параметр, но для упрощения можно получать заново.
        // Лучше передавать question из основного метода, но тогда сигнатуры изменятся.
        // Я покажу вариант с получением через сервис.
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

    private InlineKeyboardMarkup buildResultKeyboardAfterWrong(UserContext context, boolean isLast) {
        if (isLast) {
            // Для последнего вопроса после неправильного ответа кнопка "Далее" ведёт к статистике
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_NEXT).callbackData(CALLBACK_NEXT_QUESTION)}
            );
        } else {
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_NEXT).callbackData(CALLBACK_NEXT_QUESTION)}
            );
        }
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
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.isTestMode()) {
            List<Long> questionIds = context.getTestQuestionIds();
            int currentIdx = context.getCurrentTestQuestionIndex();
            if (currentIdx + 1 < questionIds.size()) {
                // Есть следующий вопрос
                context.setCurrentTestQuestionIndex(currentIdx + 1);
                sessionService.updateSessionContext(userId, context);
                navigationService.getQuestionWithImagesAndOptions(questionIds.get(currentIdx + 1))
                        .ifPresent(question -> showTestQuestion(userId, messageId, question));
            } else {
                // Это был последний вопрос – показываем статистику
                showTestSummary(userId, messageId);
            }
        } else {
            Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
            List<Question> questions = navigationService.getQuestionsForBlock(currentBlockId);
            if (context.getCurrentBlockQuestionIndex() == -1) {
                // Пользователь нажал "К вопросам"
                if (!questions.isEmpty()) {
                    context.setCurrentBlockQuestionIndex(0);
                    sessionService.updateSessionContext(userId, context);
                    navigationService.getQuestionWithImagesAndOptions(questions.get(0).getId())
                            .ifPresent(question -> showTestQuestion(userId, messageId, question));
                } else {
                    // Блок без вопросов – переходим к следующему блоку
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
                    // Достигнут конец блока – переходим к следующему блоку
                    courseNavHandler.handleNextBlock(userId, messageId);
                }
            }
        }
    }

    public void handlePrevQuestion(Long userId, Integer messageId) {
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
                // Первый вопрос теста – возвращаемся к соответствующему меню
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
                // Уже на тексте блока, нажатие "Назад" должно перейти к предыдущему блоку
                courseNavHandler.handlePrevBlock(userId, messageId);
            } else {
                int prevIdx = context.getCurrentBlockQuestionIndex() - 1;
                if (prevIdx >= 0) {
                    context.setCurrentBlockQuestionIndex(prevIdx);
                    sessionService.updateSessionContext(userId, context);
                    navigationService.getQuestionWithImagesAndOptions(questions.get(prevIdx).getId())
                            .ifPresent(question -> showTestQuestion(userId, messageId, question));
                } else {
                    // Возвращаемся к тексту блока
                    context.setCurrentBlockQuestionIndex(-1);
                    sessionService.updateSessionContext(userId, context);
                    courseNavHandler.showBlockContent(userId, messageId, currentBlockId); // нужно сделать метод public
                }
            }
        }
    }

    public void handleBackToBlockText(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
        context.setCurrentBlockQuestionIndex(-1);
        sessionService.updateSessionContext(userId, context);
        courseNavHandler.showBlockContent(userId, messageId, currentBlockId);
    }

    // ================== Внутренние методы ==================
    private void showTestQuestion(Long userId, Integer messageId, Question question) {
        UserContext context = sessionService.getCurrentContext(userId);
        String backCallbackData;
        int currentNumber;
        int totalQuestions;

        if (context.isTestMode()) {
            currentNumber = context.getCurrentTestQuestionIndex() + 1;
            totalQuestions = context.getTestQuestionIds().size();

            if (currentNumber == 1) {
                // Первый вопрос – возврат в меню (список курсов/разделов/тем)
                backCallbackData = getBackCallbackData(context);
            } else {
                // Второй и последующие – переход к предыдущему вопросу
                backCallbackData = CALLBACK_PREV_QUESTION;
            }
        } else {
            backCallbackData = CALLBACK_PREV_QUESTION;
            currentNumber = context.getCurrentBlockQuestionIndex() + 1;
            totalQuestions = context.getCurrentBlockQuestionIds().size();
        }

        sendMediaGroup(userId, question.getImages());

        String text = String.format(FORMAT_QUESTION, currentNumber, totalQuestions, question.getText());
        List<AnswerOption> options = new ArrayList<>(navigationService.getAnswerOptionsForQuestion(question.getId()));
        Collections.shuffle(options);
        InlineKeyboardMarkup keyboard = buildAnswerOptionsKeyboard(options, question.getId(), backCallbackData);

        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private InlineKeyboardMarkup buildAnswerOptionsKeyboard(List<AnswerOption> options, Long questionId, String backCallbackData) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (AnswerOption opt : options) {
            rows.add(new InlineKeyboardButton[]{
                    new InlineKeyboardButton(opt.getText()).callbackData("answer:" + questionId + ":" + opt.getId())
            });
        }
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_BACK).callbackData(backCallbackData)});
        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }
}