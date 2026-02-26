package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.BlockImage;
import com.lbt.telegram_learning_bot.entity.QuestionImage;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InputMedia;
import com.pengrad.telegrambot.model.request.InputMediaPhoto;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public abstract class BaseHandler {
    protected final TelegramBot telegramBot;
    protected final UserSessionService sessionService;
    protected final NavigationService navigationService;
    protected final AdminUserRepository adminUserRepository;

    public BaseHandler(TelegramBot telegramBot,
                       UserSessionService sessionService,
                       NavigationService navigationService,
                       AdminUserRepository adminUserRepository) {
        this.telegramBot = telegramBot;
        this.sessionService = sessionService;
        this.navigationService = navigationService;
        this.adminUserRepository = adminUserRepository;
    }

    protected void sendMainMenu(Long userId, Integer messageId) {
        String text = MSG_MAIN_MENU;
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_MY_COURSES).callbackData(CALLBACK_MY_COURSES),
                        new InlineKeyboardButton(BUTTON_ALL_COURSES).callbackData(CALLBACK_ALL_COURSES),
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_SEARCH).callbackData(CALLBACK_SEARCH_COURSES),
                        new InlineKeyboardButton(BUTTON_STATISTICS).callbackData(CALLBACK_STATISTICS)
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_MISTAKES).callbackData(CALLBACK_MY_MISTAKES)
                }
        );
        if (isAdmin(userId)) {
            keyboard.addRow(
                    new InlineKeyboardButton(BUTTON_CREATE_COURSE).callbackData(CALLBACK_CREATE_COURSE),
                    new InlineKeyboardButton(BUTTON_EDIT_COURSE).callbackData(CALLBACK_EDIT_COURSE),
                    new InlineKeyboardButton(BUTTON_DELETE_COURSE).callbackData(CALLBACK_DELETE_COURSE)
            );
        }
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    protected boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
    }
    // ================== Отправка и редактирование сообщений ==================
    protected void sendMessage(Long userId, String text) {
        sendMessage(userId, text, null);
    }

    protected void sendMessage(Long userId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            UserContext context = sessionService.getCurrentContext(userId);
            Integer prevId = context.getLastInteractiveMessageId();
            if (prevId != null) {
                deleteMessage(userId, prevId);
            }
            SendMessage request = new SendMessage(userId, text).replyMarkup(keyboard);
            request.parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown);
            SendResponse response = telegramBot.execute(request);
            if (!response.isOk()) {
                log.error("Failed to send message to user {}: {}", userId, response.description());
            } else {
                context.setLastInteractiveMessageId(response.message().messageId());
                sessionService.updateSessionContext(userId, context);
            }
        } else {
            SendMessage request = new SendMessage(userId, text).parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown);
            var response = telegramBot.execute(request);
            if (!response.isOk()) {
                log.error("Failed to send message to user {}: {}", userId, response.description());
            }
        }
    }

    protected void editMessage(Long userId, Integer messageId, String text) {
        editMessage(userId, messageId, text, null);
    }

    protected void editMessage(Long userId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText request = new EditMessageText(userId, messageId, text);
        request.parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown);
        if (keyboard != null) {
            request.replyMarkup(keyboard);
        }
        var response = telegramBot.execute(request);
        if (!response.isOk()) {
            log.error("Failed to edit message for user {}: {}", userId, response.description());
        } else {
            if (keyboard == null) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setLastInteractiveMessageId(null);
                sessionService.updateSessionContext(userId, context);
            }
        }
    }

    protected void deleteMessage(Long userId, Integer messageId) {
        if (messageId == null) return;
        DeleteMessage request = new DeleteMessage(userId, messageId);
        var response = telegramBot.execute(request);
        if (!response.isOk()) {
            log.debug("Failed to delete message {} for user {}: {}", messageId, userId, response.description());
        }
    }

    protected void sendMediaGroup(Long userId, List<?> images) {
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.getLastMediaMessageIds() != null) {
            for (Integer msgId : context.getLastMediaMessageIds()) {
                deleteMessage(userId, msgId);
            }
            context.getLastMediaMessageIds().clear();
            sessionService.updateSessionContext(userId, context);
        }

        if (images.isEmpty()) {
            return;
        }

        List<InputMedia> media = new ArrayList<>();
        for (Object img : images) {
            String filePath = null;
            String description = null;
            if (img instanceof BlockImage) {
                filePath = ((BlockImage) img).getFilePath();
                description = ((BlockImage) img).getDescription();
            } else if (img instanceof QuestionImage) {
                filePath = ((QuestionImage) img).getFilePath();
                description = ((QuestionImage) img).getDescription();
            } else {
                continue;
            }
            if (filePath == null || filePath.isEmpty()) continue;
            InputMediaPhoto photo = new InputMediaPhoto(new File(filePath));
            if (description != null && !description.isEmpty()) {
                photo.caption(description);
            }
            media.add(photo);
        }
        if (media.isEmpty()) return;

        SendMediaGroup request = new SendMediaGroup(userId, media.toArray(new InputMedia[0]));
        var response = telegramBot.execute(request);
        if (response.isOk()) {
            Message[] messagesArray = response.messages();
            List<Message> messages = Arrays.asList(messagesArray);
            List<Integer> newIds = messages.stream().map(Message::messageId).toList();
            context.setLastMediaMessageIds(newIds);
            sessionService.updateSessionContext(userId, context);
        } else {
            log.error("Failed to send media group to user {}: {}", userId, response.description());
        }
    }

    protected void clearMediaMessages(Long userId) {
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.getLastMediaMessageIds() != null) {
            for (Integer msgId : context.getLastMediaMessageIds()) {
                deleteMessage(userId, msgId);
            }
            context.getLastMediaMessageIds().clear();
            sessionService.updateSessionContext(userId, context);
        }
    }

    protected Integer sendProgressMessage(Long userId) {
        SendMessage request = new SendMessage(userId, MSG_PROGRESS);
        SendResponse response = telegramBot.execute(request);
        if (response.isOk()) {
            return response.message().messageId();
        }
        return null;
    }

    // ================== Вспомогательные клавиатуры ==================
    protected InlineKeyboardMarkup createRetryOrCancelKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_RETRY).callbackData(CALLBACK_RETRY)},
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_CANCEL).callbackData(CALLBACK_MAIN_MENU)}
        );
    }

    protected InlineKeyboardMarkup createCancelKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_CANCEL).callbackData(CALLBACK_MAIN_MENU)}
        );
    }

    protected InlineKeyboardMarkup createBackToMainKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)}
        );
    }

    protected InlineKeyboardMarkup createSearchNotFoundKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_RETRY_SEARCH).callbackData(CALLBACK_SEARCH_COURSES)},
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)}
        );
    }

    protected InlineKeyboardMarkup createCancelKeyboardWithBackToTopics() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_CANCEL).callbackData(CALLBACK_BACK_TO_TOPICS)}
        );
    }

    protected InlineKeyboardMarkup createStatisticsKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton(BUTTON_EXPORT_PDF).callbackData(CALLBACK_EXPORT_PDF),
                        new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)
                }
        );
    }

    protected String formatStudyTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else {
            return String.format("%d мин", minutes);
        }
    }

    protected String formatLastAccessed(Instant instant) {
        if (instant == null) return "никогда";

        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();

        long days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), now.toLocalDate());

        if (days == 0) {
            return "сегодня";
        } else if (days == 1) {
            return "вчера";
        } else if (days < 7) {
            return days + " дня назад";
        } else if (days < 30) {
            long weeks = days / 7;
            return weeks + " " + (weeks == 1 ? "неделю" : "недели") + " назад";
        } else if (days < 365) {
            long months = days / 30;
            return months + " " + (months == 1 ? "месяц" : "месяца") + " назад";
        } else {
            long years = days / 365;
            return years + " " + (years == 1 ? "год" : "года") + " назад";
        }
    }

    protected InlineKeyboardMarkup createAdminCancelKeyboardWithBackToTopics() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{new InlineKeyboardButton(BUTTON_CANCEL).callbackData(CALLBACK_ADMIN_BACK_TO_TOPICS)}
        );
    }
}