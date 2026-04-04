package com.lbt.telegram_learning_bot.service;

import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

public class PdfPageEventHandler implements IEventHandler {

    private final PdfFont font;
    private final String headerText;

    public PdfPageEventHandler(String headerText) throws IOException {
        this.headerText = headerText;
        ClassPathResource resource = new ClassPathResource("fonts/DejaVuSans.ttf");
        this.font = PdfFontFactory.createFont(resource.getURL().toString(),
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }

    @Override
    public void handleEvent(Event event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
        PdfDocument pdfDoc = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        int pageNumber = pdfDoc.getPageNumber(page);
        int totalPages = pdfDoc.getNumberOfPages();   // ← всё равно может быть неточным

        Rectangle pageSize = page.getPageSize();
        PdfCanvas pdfCanvas = new PdfCanvas(page);
        Canvas canvas = new Canvas(pdfCanvas, pageSize);

        // Номер страницы внизу по центру
        float x = pageSize.getWidth() / 2;
        float y = pageSize.getBottom() + 20;

        Paragraph pageNum = new Paragraph(String.format("Страница %d из %d", pageNumber, totalPages))
                .setFont(font)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);

        canvas.showTextAligned(pageNum, x, y, TextAlignment.CENTER);

        // Верхний колонтитул (только со 2-й страницы)
        if (headerText != null && !headerText.isEmpty() && pageNumber > 1) {
            float topY = pageSize.getTop() - 20;
            Paragraph header = new Paragraph(headerText)
                    .setFont(font)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER);
            canvas.showTextAligned(header, x, topY, TextAlignment.CENTER);
        }

        canvas.close();
    }
}