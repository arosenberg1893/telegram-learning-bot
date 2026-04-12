package com.lbt.telegram_learning_bot.service;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.lbt.telegram_learning_bot.entity.Course;
import com.lbt.telegram_learning_bot.repository.CourseRepository;
import com.lbt.telegram_learning_bot.repository.UserProgressRepository;
import com.lbt.telegram_learning_bot.service.cloud.CloudStorageFacade;
import com.lbt.telegram_learning_bot.service.cloud.YandexDiskStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final CourseRepository courseRepository;
    private final UserProgressRepository userProgressRepository;
    private final NavigationService navigationService;
    private final YandexDiskStorageService yandexDiskStorage;
    private final CloudStorageFacade cloudStorageFacade;

    private String formatStudyTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else {
            return String.format("%d мин", minutes);
        }
    }

    public String saveStatisticsPdf(Long userId, String userName) throws Exception {
        byte[] pdfBytes = generateStatisticsPdf(userId, userName);
        String fileName = String.format("Статистика обучения %s на %s.pdf",
                userName, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH-mm")));
        return cloudStorageFacade.uploadWithFallback(pdfBytes, fileName);
    }

    public byte[] generateStatisticsPdf(Long userId, String userName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        PdfFont font = loadFont();

        Paragraph title = new Paragraph(PDF_TITLE)
                .setFont(font)
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        document.add(new Paragraph(PDF_USER_LABEL + userName)
                .setFont(font)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph(PDF_DATE_LABEL + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .setFont(font)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n"));

        long totalStarted = navigationService.getTotalStartedCourses(userId);
        long completedCourses = navigationService.getCompletedCoursesCount(userId);
        String hardestCourse = navigationService.getHardestCourse(userId);

        document.add(new Paragraph(PDF_STATS_GENERAL)
                .setFont(font)
                .setFontSize(16)
                .setBold());
        document.add(new Paragraph(PDF_TOTAL_STARTED + totalStarted)
                .setFont(font));
        document.add(new Paragraph(PDF_COMPLETED + completedCourses)
                .setFont(font));
        document.add(new Paragraph(PDF_HARDEST + hardestCourse)
                .setFont(font));
        long totalStudySeconds = navigationService.getTotalStudySecondsForUser(userId);
        document.add(new Paragraph(PDF_TOTAL_TIME + formatStudyTime(totalStudySeconds))
                .setFont(font));
        document.add(new Paragraph("\n"));

        document.add(new Paragraph(PDF_PROGRESS)
                .setFont(font)
                .setFontSize(16)
                .setBold());

        Table table = new Table(UnitValue.createPercentArray(new float[]{40, 20, 40}));
        table.setWidth(UnitValue.createPercentValue(100));

        table.addHeaderCell(createCell(PDF_COLUMN_COURSE, font, true));
        table.addHeaderCell(createCell(PDF_COLUMN_PROGRESS, font, true));
        table.addHeaderCell(createCell(PDF_COLUMN_STATUS, font, true));

        List<Course> courses = courseRepository.findAll();
        for (Course course : courses) {
            long totalQuestions = courseRepository.countQuestionsByCourseId(course.getId());
            if (totalQuestions == 0) continue;

            long answeredQuestions = userProgressRepository.countDistinctAnsweredQuestionsByUserAndCourse(userId, course.getId());
            int percent = (int) Math.round(answeredQuestions * 100.0 / totalQuestions);

            String statusText;
            if (answeredQuestions == 0) {
                statusText = PDF_STATUS_NOT_STARTED;
            } else if (answeredQuestions < totalQuestions) {
                statusText = PDF_STATUS_IN_PROGRESS;
            } else {
                statusText = PDF_STATUS_COMPLETED;
            }

            table.addCell(createCell(course.getTitle(), font, false));
            table.addCell(createCell(percent + "%", font, false));
            table.addCell(createCell(statusText, font, false));
        }

        document.add(table);
        document.close();

        return baos.toByteArray();
    }

    private PdfFont loadFont() throws IOException {
        ClassPathResource resource = new ClassPathResource("fonts/DejaVuSans.ttf");
        return PdfFontFactory.createFont(resource.getURL().toString(), PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }

    private Cell createCell(String text, PdfFont font, boolean isHeader) {
        Cell cell = new Cell().add(new Paragraph(text).setFont(font));
        if (isHeader) {
            cell.setBold();
            cell.setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY);
        }
        return cell;
    }
}
