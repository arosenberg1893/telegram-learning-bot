package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.PaginationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Component
@RequiredArgsConstructor
public class KeyboardBuilder {

    private final NavigationService navigationService;

    // ========== Пользовательские клавиатуры (BotKeyboard) ==========

    public BotKeyboard buildCoursesKeyboardBot(PaginationResult<Course> result, Long userId,
                                               String source, String selectAction, boolean withTest) {
        List<Long> courseIds = result.getItems().stream().map(Course::getId).toList();
        Map<Long, String> courseStatuses = navigationService.getCourseStatusesForUser(userId, courseIds);
        Map<Long, String> courseTestStatuses = withTest ? navigationService.getCourseTestStatusesForUser(userId, courseIds) : Collections.emptyMap();

        BotKeyboard keyboard = new BotKeyboard();
        for (Course course : result.getItems()) {
            String courseEmoji = courseStatuses.getOrDefault(course.getId(), EMOJI_NOT_STARTED);
            String title = courseEmoji + " " + course.getTitle();

            if (SOURCE_MY_COURSES.equals(source)) {
                String timeStr = navigationService.getLastAccessedTime(userId, course.getId());
                if (!timeStr.isEmpty()) {
                    title += " (" + timeStr + ")";
                }
            }

            if (withTest) {
                String testEmoji = courseTestStatuses.getOrDefault(course.getId(), EMOJI_NOT_STARTED);
                String testButtonText = testEmoji + " Тест";
                keyboard.addRow(
                        BotButton.callback(title, selectAction + ":" + course.getId()),
                        BotButton.callback(testButtonText, "test_course:" + course.getId())
                );
            } else {
                keyboard.addRow(BotButton.callback(title, selectAction + ":" + course.getId()));
            }
        }

        addPaginationButtonsBot(keyboard, result, CALLBACK_COURSES_PAGE, source);
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
        return keyboard;
    }

    public BotKeyboard buildSectionsKeyboardBot(PaginationResult<Section> result, Long userId,
                                                Long courseId, boolean withTest, String selectAction) {
        BotKeyboard keyboard = new BotKeyboard();
        for (Section section : result.getItems()) {
            String sectionEmoji = navigationService.getSectionStatusEmoji(userId, section.getId());
            String title = sectionEmoji + " " + section.getTitle();
            if (withTest) {
                String testEmoji = navigationService.getSectionTestStatus(userId, section.getId());
                String testButtonText = testEmoji + " Тест";
                keyboard.addRow(
                        BotButton.callback(title, selectAction + ":" + section.getId()),
                        BotButton.callback(testButtonText, "test_section:" + section.getId())
                );
            } else {
                keyboard.addRow(BotButton.callback(title, selectAction + ":" + section.getId()));
            }
        }

        addPaginationButtonsBot(keyboard, result, CALLBACK_SECTIONS_PAGE, courseId.toString());
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_BACK_TO_COURSES));
        return keyboard;
    }

    public BotKeyboard buildTopicsKeyboardBot(PaginationResult<Topic> result, Long userId,
                                              Long sectionId, boolean withTest, String selectAction) {
        BotKeyboard keyboard = new BotKeyboard();
        for (Topic topic : result.getItems()) {
            String topicEmoji = navigationService.getTopicStatusEmoji(userId, topic.getId());
            String title = topicEmoji + " " + topic.getTitle();
            if (withTest) {
                String testEmoji = navigationService.getTopicTestStatus(userId, topic.getId());
                String testButtonText = testEmoji + " Тест";
                keyboard.addRow(
                        BotButton.callback(title, selectAction + ":" + topic.getId()),
                        BotButton.callback(testButtonText, "test_topic:" + topic.getId())
                );
            } else {
                keyboard.addRow(BotButton.callback(title, selectAction + ":" + topic.getId()));
            }
        }

        addPaginationButtonsBot(keyboard, result, CALLBACK_TOPICS_PAGE, sectionId.toString());
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_BACK_TO_SECTIONS));
        return keyboard;
    }

    public BotKeyboard buildBlockNavigationKeyboardBot(UserContext context) {
        Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
        Block block = navigationService.getBlock(currentBlockId).orElse(null);
        if (block == null) return BotKeyboard.backToMain();

        List<Question> questions = navigationService.getQuestionsForBlock(currentBlockId);
        boolean hasQuestions = !questions.isEmpty();
        boolean isLastBlock = context.getCurrentBlockIndex() == context.getCurrentTopicBlockIds().size() - 1;

        BotKeyboard keyboard = new BotKeyboard();
        if (context.getCurrentBlockQuestionIndex() == -1) {
            if (hasQuestions) {
                keyboard.addRow(BotButton.callback(BUTTON_TO_QUESTIONS, CALLBACK_NEXT_QUESTION));
            } else {
                if (isLastBlock) {
                    keyboard.addRow(BotButton.callback(BUTTON_FINISH_TOPIC, CALLBACK_BACK_TO_SECTIONS));
                } else {
                    keyboard.addRow(BotButton.callback(BUTTON_NEXT_BLOCK, CALLBACK_NEXT_BLOCK));
                }
            }
        }

        if (context.getCurrentBlockIndex() > 0) {
            keyboard.addRow(BotButton.callback(BUTTON_PREV_BLOCK, CALLBACK_PREV_BLOCK));
        }

        keyboard.addRow(BotButton.callback(BUTTON_BACK_TO_TOPICS_LIST, CALLBACK_BACK_TO_TOPICS));
        return keyboard;
    }

    // ========== Административные клавиатуры (BotKeyboard) ==========

    public BotKeyboard buildCoursesKeyboardForAdminBot(PaginationResult<Course> result,
                                                       String source, String selectAction) {
        BotKeyboard keyboard = new BotKeyboard();
        for (Course course : result.getItems()) {
            keyboard.addRow(BotButton.callback(course.getTitle(), selectAction + ":" + course.getId()));
        }
        addPaginationButtonsBot(keyboard, result, CALLBACK_ADMIN_COURSES_PAGE, source);
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
        return keyboard;
    }

    public BotKeyboard buildSectionsKeyboardForAdminBot(PaginationResult<Section> result,
                                                        Long courseId,
                                                        String selectAction,
                                                        String backCallback) {
        BotKeyboard keyboard = new BotKeyboard();
        for (Section section : result.getItems()) {
            keyboard.addRow(BotButton.callback(section.getTitle(), selectAction + ":" + section.getId()));
        }
        addPaginationButtonsBot(keyboard, result, CALLBACK_ADMIN_SECTIONS_PAGE, courseId.toString());
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, backCallback));
        return keyboard;
    }

    public BotKeyboard buildTopicsKeyboardForAdminBot(PaginationResult<Topic> result,
                                                      Long sectionId,
                                                      String selectAction,
                                                      String backCallback) {
        BotKeyboard keyboard = new BotKeyboard();
        for (Topic topic : result.getItems()) {
            keyboard.addRow(BotButton.callback(topic.getTitle(), selectAction + ":" + topic.getId()));
        }
        addPaginationButtonsBot(keyboard, result, CALLBACK_ADMIN_TOPICS_PAGE, sectionId.toString());
        keyboard.addRow(BotButton.callback(BUTTON_MAIN_MENU, backCallback));
        return keyboard;
    }

    // ========== Вспомогательный метод для пагинации ==========
    private void addPaginationButtonsBot(BotKeyboard keyboard, PaginationResult<?> result,
                                         String callbackPrefix, String callbackDataSuffix) {
        List<BotButton> navButtons = new ArrayList<>();
        if (result.isHasPrevious()) {
            navButtons.add(BotButton.callback(BUTTON_PREV,
                    callbackPrefix + ":" + callbackDataSuffix + ":" + (result.getCurrentPage() - 1)));
        }
        if (result.isHasNext()) {
            navButtons.add(BotButton.callback(BUTTON_NEXT_PAGE,
                    callbackPrefix + ":" + callbackDataSuffix + ":" + (result.getCurrentPage() + 1)));
        }
        if (!navButtons.isEmpty()) {
            keyboard.addRow(navButtons.toArray(new BotButton[0]));
        }
    }

    // Старые методы (не используются, оставлены для совместимости, но теперь возвращают пустую клавиатуру)
    public BotKeyboard buildCoursesKeyboard(PaginationResult<Course> result, Long userId, String source, String selectAction, boolean withTest) {
        return new BotKeyboard();
    }

    public BotKeyboard buildSectionsKeyboard(PaginationResult<Section> result, Long userId, Long courseId, boolean withTest, String selectAction) {
        return new BotKeyboard();
    }

    public BotKeyboard buildTopicsKeyboard(PaginationResult<Topic> result, Long userId, Long sectionId, boolean withTest, String selectAction) {
        return new BotKeyboard();
    }

    public BotKeyboard buildBlockNavigationKeyboard(UserContext context) {
        return new BotKeyboard();
    }

    public BotKeyboard buildCoursesKeyboardForAdmin(PaginationResult<Course> result, String source, String selectAction) {
        return new BotKeyboard();
    }

    public BotKeyboard buildSectionsKeyboardForAdmin(PaginationResult<Section> result, Long courseId, String selectAction, String backCallback) {
        return new BotKeyboard();
    }

    public BotKeyboard buildTopicsKeyboardForAdmin(PaginationResult<Topic> result, Long sectionId, String selectAction, String backCallback) {
        return new BotKeyboard();
    }
}