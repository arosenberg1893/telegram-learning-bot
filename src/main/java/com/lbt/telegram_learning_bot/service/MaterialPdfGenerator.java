package com.lbt.telegram_learning_bot.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.DottedLine;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.repository.BlockImageRepository;
import com.lbt.telegram_learning_bot.repository.QuestionImageRepository;
import com.lbt.telegram_learning_bot.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Генератор PDF-файлов с учебными материалами.
 *
 * Архитектура (двухпроходная):
 *   Проход 1 — контент рендерится во временный буфер для получения точных номеров страниц.
 *   Проход 2 — финальный документ: титул → оглавление (с реальными номерами, кликабельное)
 *              → контент. PDF-закладки (Outlines) прописываются в финальный документ.
 *
 * Дизайн:
 *   - Шрифт DejaVuSans (поддержка кириллицы).
 *   - Титул: тёмный блок на всю ширину, белый заголовок, описание, акцентная полоса.
 *   - Заголовки разделов: акцентный цвет, горизонтальная линия снизу.
 *   - Заголовки тем: тёмный, вертикальная полоса слева.
 *   - Описания и тела: выравнивание по ширине, отступ первой строки, без пустых строк.
 *   - Вопросы: светло-серый фон, левая полоса, зелёный правильный ответ.
 *   - Оглавление: точечный заполнитель (DottedLine), кликабельные заголовки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialPdfGenerator {

    private final QuestionRepository questionRepository;
    private final BlockImageRepository blockImageRepository;
    private final QuestionImageRepository questionImageRepository;

    // Шрифт
    private static final String FONT_PATH       = "fonts/DejaVuSans.ttf";
    private static final float  IMAGE_MAX_WIDTH  = 470f;
    private static final float  IMAGE_MAX_HEIGHT = 360f;
    private static final float  PAGE_MARGIN      = 56f;

    // Цвета
    private static final DeviceRgb C_TITLE_BG = new DeviceRgb(0x1E, 0x2A, 0x3A);
    private static final DeviceRgb C_ACCENT   = new DeviceRgb(0x2E, 0x6D, 0xA4);
    private static final DeviceRgb C_SECTION  = new DeviceRgb(0x1A, 0x46, 0x72);
    private static final DeviceRgb C_TOPIC    = new DeviceRgb(0x22, 0x22, 0x22);
    private static final DeviceRgb C_BODY     = new DeviceRgb(0x33, 0x33, 0x33);
    private static final DeviceRgb C_LINK     = new DeviceRgb(0x2E, 0x6D, 0xA4);
    private static final DeviceRgb C_Q_BG     = new DeviceRgb(0xF4, 0xF6, 0xF9);
    private static final DeviceRgb C_CORRECT  = new DeviceRgb(0x1A, 0x70, 0x35);
    private static final DeviceRgb C_SUBTITLE = new DeviceRgb(0xCC, 0xDD, 0xEE);

    private record TocEntry(String title, int level, int page) {}

    // =========================================================================
    //  Публичные методы
    // =========================================================================

    public byte[] generateTopicPdf(Topic topic, String userName, boolean includeQuestions) throws Exception {
        PdfFont font = loadFont();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        pdfDoc.setDefaultPageSize(PageSize.A4);
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PdfPageEventHandler(topic.getTitle()));
        Document doc = makeDocument(pdfDoc);

        addTitlePage(doc, font, topic.getTitle(), topic.getDescription(), userName);

        List<Block> blocks = topic.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            doc.add(bodyPara(font, "В теме нет учебных блоков."));
        } else {
            for (Block b : blocks) renderBlock(doc, font, b, includeQuestions);
        }
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generateSectionPdf(Section section, String userName, boolean includeQuestions) throws Exception {
        // Проход 1: сбор TocEntry
        List<TocEntry> raw = new ArrayList<>();
        int sectionNumber = section.getOrderIndex() + 1;
        collectSectionToc(section, sectionNumber, includeQuestions, raw);

        int tocPages = measureTocPages(raw);
        int offset = 1 + tocPages;
        List<TocEntry> toc = applyOffset(raw, offset);

        // Проход 2: финальный документ
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        pdfDoc.setDefaultPageSize(PageSize.A4);
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PdfPageEventHandler(section.getTitle()));
        Document doc = makeDocument(pdfDoc);
        PdfFont font = loadFont();

        addTitlePage(doc, font, section.getTitle(), section.getDescription(), userName);
        renderToc(doc, pdfDoc, font, toc);
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        renderSection(doc, font, section, sectionNumber, includeQuestions);
        addOutlines(pdfDoc, toc);

        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            String destName = "toc-dest-" + i;
            PdfExplicitDestination dest = PdfExplicitDestination.createFit(pdfDoc.getPage(i));
            pdfDoc.addNamedDestination(destName, dest.getPdfObject());
        }
        doc.close();

        fixFootersWithCorrectTotal(baos, section.getTitle());
        return baos.toByteArray();
    }

    public byte[] generateCoursePdf(Course course, String userName, boolean includeQuestions) throws Exception {
        // Проход 1: сбор TocEntry
        List<TocEntry> raw = new ArrayList<>();
        collectCourseToc(course, includeQuestions, raw);

        int tocPages = measureTocPages(raw);
        int offset   = 1 + tocPages;
        List<TocEntry> toc = applyOffset(raw, offset);

        // Проход 2: финальный документ — свежий шрифт
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        pdfDoc.setDefaultPageSize(PageSize.A4);
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PdfPageEventHandler(course.getTitle()));
        Document doc = makeDocument(pdfDoc);
        PdfFont font = loadFont();

        addTitlePage(doc, font, course.getTitle(), course.getDescription(), userName);
        renderToc(doc, pdfDoc, font, toc);
        // Первый раздел/тема всегда начинается с новой страницы после оглавления
        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        renderCourse(doc, font, course, includeQuestions);
        addOutlines(pdfDoc, toc);

        // Создаём именованные destinations для всех страниц (чтобы ссылки работали надёжно)
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            String destName = "toc-dest-" + i;
            PdfExplicitDestination dest = PdfExplicitDestination.createFit(pdfDoc.getPage(i));
            pdfDoc.addNamedDestination(destName, dest.getPdfObject());
        }
        doc.close();

        // ← Исправляем футеры с правильным общим количеством страниц
        fixFootersWithCorrectTotal(baos, course.getTitle());   // или course.getTitle()
        return baos.toByteArray();
    }

    // =========================================================================
    //  Проход 1: сбор TocEntry (рендеринг в буфер)
    // =========================================================================

    private void collectSectionToc(Section section, int sectionNumber, boolean includeQuestions,
                                   List<TocEntry> entries) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument tmp = new PdfDocument(new PdfWriter(buf));
        tmp.setDefaultPageSize(PageSize.A4);
        Document d = makeDocument(tmp);
        PdfFont font = loadFont();

        if (section.getTopics() != null) {
            int topicNum = 1;
            for (Topic t : getSortedTopics(section.getTopics())) {
                // Формируем заголовок с номером
                String numberedTitle = sectionNumber + "." + topicNum + " " + t.getTitle();
                d.add(topicHeader(font, numberedTitle));
                entries.add(new TocEntry(numberedTitle, 0, tmp.getNumberOfPages()));
                if (hasText(t.getDescription())) d.add(descPara(font, t.getDescription()));
                if (t.getBlocks() != null) {
                    for (Block b : t.getBlocks()) renderBlock(d, font, b, includeQuestions);
                }
                topicNum++;
            }
        }
        d.close();
    }

    private void collectCourseToc(Course course, boolean includeQuestions,
                                  List<TocEntry> entries) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument tmp = new PdfDocument(new PdfWriter(buf));
        tmp.setDefaultPageSize(PageSize.A4);
        Document d = makeDocument(tmp);
        PdfFont font = loadFont();

        if (course.getSections() != null) {
            int sn = 1;
            for (Section s : course.getSections()) {
                if (sn > 1) {
                    d.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }

                String sTitle = sn + ". " + s.getTitle();
                d.add(sectionHeader(font, sTitle));                    // ← сначала добавляем
                entries.add(new TocEntry(sTitle, 0, tmp.getNumberOfPages())); // ← потом записываем

                if (hasText(s.getDescription())) d.add(descPara(font, s.getDescription()));

                if (s.getTopics() != null) {
                    int tn = 1;
                    for (Topic t : getSortedTopics(s.getTopics())) {         // ← изменил
                        String tTitle = sn + "." + tn + " " + t.getTitle();
                        d.add(topicHeader(font, tTitle));              // ← сначала
                        entries.add(new TocEntry(tTitle, 1, tmp.getNumberOfPages())); // ← потом

                        if (hasText(t.getDescription())) d.add(descPara(font, t.getDescription()));
                        if (t.getBlocks() != null) {
                            for (Block b : t.getBlocks()) {
                                renderBlock(d, font, b, includeQuestions);
                            }
                        }
                        tn++;
                    }
                }
                sn++;
            }
        }
        d.close();
    }

    // =========================================================================
    //  Замер страниц оглавления
    // =========================================================================

    private int measureTocPages(List<TocEntry> entries) throws Exception {
        if (entries.isEmpty()) return 0;

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfDocument tmp = new PdfDocument(new PdfWriter(buf));
        tmp.setDefaultPageSize(PageSize.A4);
        Document d = makeDocument(tmp);
        PdfFont font = loadFont();

        renderTocBody(d, null, font, entries, false);

        // Получаем количество страниц ДО закрытия документа!
        int pages = tmp.getNumberOfPages();

        d.close();
        return pages;
    }

    // =========================================================================
    //  Оглавление
    // =========================================================================

    private void renderToc(Document doc, PdfDocument pdfDoc, PdfFont font, List<TocEntry> entries) {
        if (entries.isEmpty()) return;
        renderTocBody(doc, pdfDoc, font, entries, true);
    }

    private void renderTocBody(Document doc, PdfDocument pdfDoc,
                               PdfFont font, List<TocEntry> entries, boolean withLinks) {
        doc.add(new Paragraph("Содержание")
                .setFont(font).setFontSize(18).setFontColor(C_SECTION).setBold()
                .setMarginBottom(14).setTextAlignment(TextAlignment.LEFT));

        float usable = PageSize.A4.getWidth() - 2 * PAGE_MARGIN;

        for (TocEntry e : entries) {
            float indent   = e.level() * 16f;
            float fontSize = e.level() == 0 ? 12f : 11f;
            boolean isTop  = e.level() == 0;

            Paragraph line = new Paragraph()
                    .setFont(font).setFontSize(fontSize)
                    .setFontColor(isTop ? C_SECTION : C_BODY)
                    .setMarginBottom(isTop ? 5 : 3)
                    .setMarginLeft(indent);
            if (isTop) line.setBold();

            if (withLinks && pdfDoc != null) {
                // ИСПОЛЬЗУЕМ РЕАЛЬНЫЙ НОМЕР СТРАНИЦЫ ИЗ TOC (без Math.min!)
                String destName = "toc-dest-" + e.page();
                Link link = new Link(e.title(), PdfAction.createGoTo(destName));
                link.setFontColor(C_LINK);
                if (isTop) link.setBold();
                line.add(link);
            } else {
                line.add(e.title());
            }

            line.addTabStops(new TabStop(usable - indent, TabAlignment.RIGHT, new DottedLine()));
            line.add(new Tab());
            line.add(new Text(String.valueOf(e.page()))
                    .setFont(font).setFontSize(fontSize).setFontColor(C_BODY));

            doc.add(line);
        }

        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(C_ACCENT, 0.8f))
                .setMarginTop(10).setMarginBottom(4));
    }

    // =========================================================================
    //  PDF-закладки
    // =========================================================================

    private void addOutlines(PdfDocument pdfDoc, List<TocEntry> entries) {
        PdfOutline root    = pdfDoc.getOutlines(false);
        PdfOutline lastTop = null;
        int total          = pdfDoc.getNumberOfPages();
        for (TocEntry e : entries) {
            int safePage = Math.min(e.page(), total);   // ← оставь эту защиту
            var dest = PdfExplicitDestination.createFit(pdfDoc.getPage(safePage));
            if (e.level() == 0) {
                lastTop = root.addOutline(e.title());
                lastTop.addDestination(dest);
            } else {
                (lastTop != null ? lastTop : root).addOutline(e.title()).addDestination(dest);
            }
        }
    }

    // =========================================================================
    //  Рендеринг контента (финальный документ)
    // =========================================================================

    private void renderSection(Document doc, PdfFont font, Section section, int sectionNumber, boolean includeQuestions) {
        List<Topic> topics = section.getTopics();
        if (topics == null || topics.isEmpty()) {
            doc.add(bodyPara(font, "В разделе нет тем."));
            return;
        }
        int topicNum = 1;
        for (Topic t : getSortedTopics(topics)) {
            String numberedTitle = sectionNumber + "." + topicNum + " " + t.getTitle();
            doc.add(topicHeader(font, numberedTitle));
            if (hasText(t.getDescription())) doc.add(descPara(font, t.getDescription()));
            if (t.getBlocks() != null) {
                for (Block b : t.getBlocks()) renderBlock(doc, font, b, includeQuestions);
            }
            topicNum++;
        }
    }

    private void renderCourse(Document doc, PdfFont font, Course course, boolean includeQuestions) {
        List<Section> sections = course.getSections();
        if (sections == null || sections.isEmpty()) {
            doc.add(bodyPara(font, "В курсе нет разделов."));
            return;
        }

        int sn = 1;
        for (Section s : sections) {
            // ← Добавляем принудительный разрыв страницы перед каждым новым разделом
            if (sn > 1) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }

            String sTitle = sn + ". " + s.getTitle();
            doc.add(sectionHeader(font, sTitle));
            if (hasText(s.getDescription())) doc.add(descPara(font, s.getDescription()));

            if (s.getTopics() != null) {
                int tn = 1;
                for (Topic t : getSortedTopics(s.getTopics())) {         // ← изменил
                    String tTitle = sn + "." + tn + " " + t.getTitle();
                    doc.add(topicHeader(font, tTitle));
                    if (hasText(t.getDescription())) doc.add(descPara(font, t.getDescription()));
                    if (t.getBlocks() != null) {
                        for (Block b : t.getBlocks()) {
                            renderBlock(doc, font, b, includeQuestions);
                        }
                    }
                    tn++;
                }
            }
            sn++;
        }
    }

    // =========================================================================
    //  Блок, вопрос, изображение
    // =========================================================================

    private void renderBlock(Document doc, PdfFont font, Block block, boolean includeQuestions) {
        if (hasText(block.getTextContent())) doc.add(bodyPara(font, block.getTextContent()));
        for (BlockImage img : blockImageRepository.findByBlockIdOrderByOrderIndexAsc(block.getId())) {
            addImageToDoc(doc, font, img.getFilePath(), img.getDescription());
        }
        if (includeQuestions) {
            List<Question> qs = block.getQuestions();   // ← вместо обращения к репозиторию
            for (Question q : qs) renderQuestion(doc, font, q);
        }
    }

    private void renderQuestion(Document doc, PdfFont font, Question q) {
        Div box = new Div()
                .setBackgroundColor(C_Q_BG)
                .setBorderLeft(new SolidBorder(C_ACCENT, 3f))
                .setPaddingLeft(10).setPaddingRight(8).setPaddingTop(7).setPaddingBottom(7)
                .setMarginTop(5).setMarginBottom(5);

        box.add(new Paragraph(q.getText())
                .setFont(font).setFontSize(11).setFontColor(C_TOPIC).setBold()
                .setTextAlignment(TextAlignment.JUSTIFIED).setMarginBottom(4));

        for (QuestionImage img : questionImageRepository.findByQuestionIdOrderByOrderIndexAsc(q.getId())) {
            addImageToDiv(box, font, img.getFilePath(), img.getDescription());
        }

        if (q.getAnswerOptions() != null) {
            String correct = q.getAnswerOptions().stream()
                    .filter(AnswerOption::getIsCorrect).map(AnswerOption::getText)
                    .findFirst().orElse("(не указан)");
            box.add(new Paragraph("✔ Правильный ответ: " + correct)
                    .setFont(font).setFontSize(10).setFontColor(C_CORRECT).setItalic()
                    .setMarginTop(2).setMarginBottom(1));
            if (hasText(q.getExplanation())) {
                box.add(new Paragraph("Пояснение: " + q.getExplanation())
                        .setFont(font).setFontSize(10).setFontColor(C_BODY).setItalic()
                        .setTextAlignment(TextAlignment.JUSTIFIED));
            }
        }
        doc.add(box);
    }

    private void addImageToDoc(Document doc, PdfFont font, String path, String desc) {
        if (!hasText(path) || !new File(path).exists()) { if (hasText(path)) log.warn("Image not found: {}", path); return; }
        try {
            Image img = new Image(com.itextpdf.io.image.ImageDataFactory.create(path))
                    .scaleToFit(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                    .setMarginTop(6).setMarginBottom(2);
            doc.add(img);
            if (hasText(desc)) doc.add(new Paragraph(desc).setFont(font).setFontSize(9)
                    .setFontColor(ColorConstants.GRAY).setItalic().setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));
        } catch (Exception e) { log.error("Failed to embed image: {}", path, e); }
    }

    private void addImageToDiv(Div div, PdfFont font, String path, String desc) {
        if (!hasText(path) || !new File(path).exists()) return;
        try {
            Image img = new Image(com.itextpdf.io.image.ImageDataFactory.create(path))
                    .scaleToFit(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                    .setMarginTop(4).setMarginBottom(2);
            div.add(img);
            if (hasText(desc)) div.add(new Paragraph(desc).setFont(font).setFontSize(9)
                    .setFontColor(ColorConstants.GRAY).setItalic().setTextAlignment(TextAlignment.CENTER));
        } catch (Exception e) { log.error("Failed to embed image in Div: {}", path, e); }
    }

    // =========================================================================
    //  Титульный лист
    // =========================================================================

    private void addTitlePage(Document doc, PdfFont font,
                              String title, String description, String userName) {
        float innerW = PageSize.A4.getWidth() - 2 * PAGE_MARGIN;

        // Тёмный блок
        Table titleBlock = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPointValue(innerW))
                .setBackgroundColor(C_TITLE_BG).setMarginBottom(0);

        Cell c = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(50)
                .add(new Paragraph(title).setFont(font).setFontSize(26).setFontColor(ColorConstants.WHITE)
                        .setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
        if (hasText(description)) {
            c.add(new Paragraph(description).setFont(font).setFontSize(12).setFontColor(C_SUBTITLE)
                    .setTextAlignment(TextAlignment.JUSTIFIED).setFirstLineIndent(20f)
                    .setMarginTop(14).setMarginBottom(0));
        }
        titleBlock.addCell(c);
        doc.add(titleBlock);

        // Акцентная полоса
        doc.add(new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPointValue(innerW))
                .setBackgroundColor(C_ACCENT).setHeight(4).setMarginBottom(28));

        doc.add(new Paragraph(
                "Сформировано: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
                        + "\nПользователь: " + userName)
                .setFont(font).setFontSize(11).setFontColor(C_BODY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(10));

        doc.add(new AreaBreak());
    }

    // =========================================================================
    //  Конструкторы параграфов
    // =========================================================================

    private Paragraph sectionHeader(PdfFont font, String text) {
        return new Paragraph(text).setFont(font).setFontSize(17).setFontColor(C_SECTION).setBold()
                .setMarginTop(20).setMarginBottom(5)
                .setBorderBottom(new SolidBorder(C_ACCENT, 1.2f)).setPaddingBottom(3);
    }

    private Paragraph topicHeader(PdfFont font, String text) {
        return new Paragraph(text).setFont(font).setFontSize(13).setFontColor(C_TOPIC).setBold()
                .setMarginTop(12).setMarginBottom(3)
                .setPaddingLeft(8).setBorderLeft(new SolidBorder(C_ACCENT, 2.5f));
    }

    private Paragraph descPara(PdfFont font, String text) {
        return new Paragraph(text).setFont(font).setFontSize(11).setFontColor(C_BODY).setItalic()
                .setTextAlignment(TextAlignment.JUSTIFIED).setFirstLineIndent(20f)
                .setMarginTop(0).setMarginBottom(3);
    }

    private Paragraph bodyPara(PdfFont font, String text) {
        return new Paragraph(text).setFont(font).setFontSize(11).setFontColor(C_BODY)
                .setTextAlignment(TextAlignment.JUSTIFIED).setFirstLineIndent(20f)
                .setMarginTop(0).setMarginBottom(3);
    }

    // =========================================================================
    //  Утилиты
    // =========================================================================

    private Document makeDocument(PdfDocument pdfDoc) {
        Document doc = new Document(pdfDoc);
        doc.setMargins(PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN + 18, PAGE_MARGIN);
        return doc;
    }

    private PdfFont loadFont() throws IOException {
        ClassPathResource res = new ClassPathResource(FONT_PATH);
        return PdfFontFactory.createFont(res.getURL().toString(), PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }

    private List<TocEntry> applyOffset(List<TocEntry> raw, int offset) {
        return raw.stream().map(e -> new TocEntry(e.title(), e.level(), e.page() + offset)).toList();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    // =========================================================================
//  Исправление футера после закрытия документа (Page X of Y)
// =========================================================================

    private void fixFootersWithCorrectTotal(ByteArrayOutputStream baos, String headerText) throws Exception {
        // Переоткрываем PDF в режиме stamping
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(baos.toByteArray()));
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(reader, writer)) {

            PdfFont font = loadFont(); // тот же шрифт

            int total = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= total; i++) {
                PdfPage page = pdfDoc.getPage(i);
                Rectangle size = page.getPageSize();

                PdfCanvas canvas = new PdfCanvas(page);
                Canvas layoutCanvas = new Canvas(canvas, size);

                float x = size.getWidth() / 2;
                float yBottom = size.getBottom() + 20;

                // Удаляем старый футер (заливаем белым)
                canvas.saveState();
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.rectangle(x - 100, yBottom - 5, 200, 20);
                canvas.fill();
                canvas.restoreState();

                // Новый правильный футер
                Paragraph pageNum = new Paragraph(String.format("Страница %d из %d", i, total))
                        .setFont(font)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER);

                layoutCanvas.showTextAligned(pageNum, x, yBottom, TextAlignment.CENTER);

                // Верхний колонтитул (со 2-й страницы)
                if (headerText != null && !headerText.isEmpty() && i > 1) {
                    float yTop = size.getTop() - 20;
                    Paragraph header = new Paragraph(headerText)
                            .setFont(font)
                            .setFontSize(10)
                            .setTextAlignment(TextAlignment.CENTER);
                    layoutCanvas.showTextAligned(header, x, yTop, TextAlignment.CENTER);
                }

                layoutCanvas.close();
            }
        }
    }

    /**
     * Надёжная сортировка тем внутри раздела.
     * Сначала по номеру в заголовке (если есть), потом — в порядке из БД.
     */
    private List<Topic> getSortedTopics(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) return List.of();

        return topics.stream()
                .sorted(Comparator.comparingInt(this::extractTopicOrder))
                .toList();
    }

    private int extractTopicOrder(Topic t) {
        String title = (t.getTitle() != null) ? t.getTitle().trim() : "";
        // Ищем в начале строки число + точку (0. , 1. , 10. и т.д.)
        if (title.matches("^\\d+\\.\\s.*")) {
            try {
                String num = title.split("\\.")[0];
                return Integer.parseInt(num);
            } catch (Exception ignored) {}
        }
        // Если номера нет — оставляем в том порядке, в котором пришла из БД
        return 9999;
    }
}