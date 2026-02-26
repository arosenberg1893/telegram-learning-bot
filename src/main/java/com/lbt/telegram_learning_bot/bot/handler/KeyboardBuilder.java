package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.PaginationResult;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
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

    // ========== Пользовательские клавиатуры ==========

    public InlineKeyboardMarkup buildCoursesKeyboard(PaginationResult<Course> result, Long userId,
                                                     String source, String selectAction, boolean withTest) {
        List<Long> courseIds = result.getItems().stream().map(Course::getId).toList();
        Map<Long, String> courseStatuses = navigationService.getCourseStatusesForUser(userId, courseIds);
        Map<Long, String> courseTestStatuses = withTest ? navigationService.getCourseTestStatusesForUser(userId, courseIds) : Collections.emptyMap();

        List<InlineKeyboardButton[]> rows = new ArrayList<>();
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
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + course.getId()),
                        new InlineKeyboardButton(testButtonText).callbackData("test_course:" + course.getId())
                });
            } else {
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + course.getId())
                });
            }
        }

        addPaginationButtons(rows, result, CALLBACK_COURSES_PAGE, source);
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    public InlineKeyboardMarkup buildSectionsKeyboard(PaginationResult<Section> result, Long userId,
                                                      Long courseId, boolean withTest, String selectAction) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Section section : result.getItems()) {
            String sectionEmoji = navigationService.getSectionStatusEmoji(userId, section.getId());
            String title = sectionEmoji + " " + section.getTitle();
            if (withTest) {
                String testEmoji = navigationService.getSectionTestStatus(userId, section.getId());
                String testButtonText = testEmoji + " Тест";
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + section.getId()),
                        new InlineKeyboardButton(testButtonText).callbackData("test_section:" + section.getId())
                });
            } else {
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + section.getId())
                });
            }
        }

        addPaginationButtons(rows, result, CALLBACK_SECTIONS_PAGE, courseId.toString());
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_BACK_TO_COURSES)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    public InlineKeyboardMarkup buildTopicsKeyboard(PaginationResult<Topic> result, Long userId,
                                                    Long sectionId, boolean withTest, String selectAction) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Topic topic : result.getItems()) {
            String topicEmoji = navigationService.getTopicStatusEmoji(userId, topic.getId());
            String title = topicEmoji + " " + topic.getTitle();
            if (withTest) {
                String testEmoji = navigationService.getTopicTestStatus(userId, topic.getId());
                String testButtonText = testEmoji + " Тест";
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + topic.getId()),
                        new InlineKeyboardButton(testButtonText).callbackData("test_topic:" + topic.getId())
                });
            } else {
                rows.add(new InlineKeyboardButton[]{
                        new InlineKeyboardButton(title).callbackData(selectAction + ":" + topic.getId())
                });
            }
        }

        addPaginationButtons(rows, result, CALLBACK_TOPICS_PAGE, sectionId.toString());
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_BACK_TO_SECTIONS)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    public InlineKeyboardMarkup buildBlockNavigationKeyboard(UserContext context) {
        Long currentBlockId = context.getCurrentTopicBlockIds().get(context.getCurrentBlockIndex());
        Block block = navigationService.getBlock(currentBlockId).orElse(null);
        if (block == null) return null;

        List<Question> questions = navigationService.getQuestionsForBlock(currentBlockId);
        boolean hasQuestions = !questions.isEmpty();
        boolean isLastBlock = context.getCurrentBlockIndex() == context.getCurrentTopicBlockIds().size() - 1;

        List<InlineKeyboardButton> buttons = new ArrayList<>();

        if (context.getCurrentBlockQuestionIndex() == -1) {
            if (hasQuestions) {
                buttons.add(new InlineKeyboardButton(BUTTON_TO_QUESTIONS).callbackData(CALLBACK_NEXT_QUESTION));
            } else {
                if (isLastBlock) {
                    buttons.add(new InlineKeyboardButton(BUTTON_FINISH_TOPIC).callbackData(CALLBACK_BACK_TO_SECTIONS));
                } else {
                    buttons.add(new InlineKeyboardButton(BUTTON_NEXT_BLOCK).callbackData(CALLBACK_NEXT_BLOCK));
                }
            }
        }

        if (context.getCurrentBlockIndex() > 0) {
            buttons.add(new InlineKeyboardButton(BUTTON_PREV_BLOCK).callbackData(CALLBACK_PREV_BLOCK));
        }

        buttons.add(new InlineKeyboardButton(BUTTON_BACK_TO_TOPICS_LIST).callbackData(CALLBACK_BACK_TO_TOPICS));
        return new InlineKeyboardMarkup(buttons.toArray(new InlineKeyboardButton[0]));
    }

    // ========== Административные клавиатуры ==========

    public InlineKeyboardMarkup buildCoursesKeyboardForAdmin(PaginationResult<Course> result,
                                                             String source, String selectAction) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Course course : result.getItems()) {
            rows.add(new InlineKeyboardButton[]{
                    new InlineKeyboardButton(course.getTitle()).callbackData(selectAction + ":" + course.getId())
            });
        }

        addPaginationButtons(rows, result, CALLBACK_ADMIN_COURSES_PAGE, source);
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    public InlineKeyboardMarkup buildSectionsKeyboardForAdmin(PaginationResult<Section> result,
                                                              Long courseId, String selectAction) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Section section : result.getItems()) {
            rows.add(new InlineKeyboardButton[]{
                    new InlineKeyboardButton(section.getTitle()).callbackData(selectAction + ":" + section.getId())
            });
        }

        addPaginationButtons(rows, result, CALLBACK_ADMIN_SECTIONS_PAGE, courseId.toString());
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_BACK_TO_COURSES)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    public InlineKeyboardMarkup buildTopicsKeyboardForAdmin(PaginationResult<Topic> result,
                                                            Long sectionId, String selectAction) {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Topic topic : result.getItems()) {
            rows.add(new InlineKeyboardButton[]{
                    new InlineKeyboardButton(topic.getTitle()).callbackData(selectAction + ":" + topic.getId())
            });
        }

        addPaginationButtons(rows, result, CALLBACK_ADMIN_TOPICS_PAGE, sectionId.toString());
        rows.add(new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_BACK_TO_SECTIONS)});

        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }

    // ========== Вспомогательные методы ==========

    private <T> void addPaginationButtons(List<InlineKeyboardButton[]> rows, PaginationResult<T> result,
                                          String callbackPrefix, String callbackDataSuffix) {
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        if (result.isHasPrevious()) {
            navButtons.add(new InlineKeyboardButton(BUTTON_PREV)
                    .callbackData(callbackPrefix + ":" + callbackDataSuffix + ":" + (result.getCurrentPage() - 1)));
        }
        if (result.isHasNext()) {
            navButtons.add(new InlineKeyboardButton(BUTTON_NEXT_PAGE)
                    .callbackData(callbackPrefix + ":" + callbackDataSuffix + ":" + (result.getCurrentPage() + 1)));
        }
        if (!navButtons.isEmpty()) {
            rows.add(navButtons.toArray(new InlineKeyboardButton[0]));
        }
    }
}