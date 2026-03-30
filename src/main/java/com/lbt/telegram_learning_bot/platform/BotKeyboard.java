package com.lbt.telegram_learning_bot.platform;

import java.util.ArrayList;
import java.util.List;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

/**
 * Платформо-независимое описание inline-клавиатуры.
 * Каждый {@link MessageSender} сам конвертирует её в нативный формат платформы.
 */
public class BotKeyboard {

    private final List<List<BotButton>> rows = new ArrayList<>();

    /** Добавить строку кнопок. */
    public BotKeyboard addRow(BotButton... buttons) {
        rows.add(List.of(buttons));
        return this;
    }

    public List<List<BotButton>> getRows() {
        return rows;
    }

    // ─── Фабричные методы ────────────────────────────────────────────────────

    public static BotKeyboard of(BotButton... buttons) {
        BotKeyboard kb = new BotKeyboard();
        kb.addRow(buttons);
        return kb;
    }

    public static BotKeyboard rows(BotButton[]... rows) {
        BotKeyboard kb = new BotKeyboard();
        for (BotButton[] row : rows) {
            kb.addRow(row);
        }
        return kb;
    }

    /** Кнопка «Главное меню». */
    public static BotKeyboard backToMain() {
        return of(BotButton.callback("🏠 Главное меню", "main_menu"));
    }

    /** Кнопка «Отмена». */
    public static BotKeyboard cancel() {
        return of(BotButton.callback("❌ Отмена", "main_menu"));
    }
}
