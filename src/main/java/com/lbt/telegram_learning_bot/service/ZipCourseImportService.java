package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.dto.CourseStructureImportDto;
import com.lbt.telegram_learning_bot.dto.SectionStructureImportDto;
import com.lbt.telegram_learning_bot.dto.TopicImportDto;
import com.lbt.telegram_learning_bot.entity.Course;
import com.lbt.telegram_learning_bot.entity.Section;
import com.lbt.telegram_learning_bot.entity.Topic;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.repository.SectionRepository;
import com.lbt.telegram_learning_bot.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZipCourseImportService {

    private final ObjectMapper objectMapper;
    private final CourseImportService courseImportService;
    private final SectionRepository sectionRepository;
    private final TopicRepository topicRepository;

    private static final Pattern SECTION_DIR_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern TOPIC_NUMBER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.json$");

    @Transactional
    public Course importCourseFromZip(InputStream zipStream) throws Exception {
        // 1. Распаковываем ZIP в память
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    files.put(entry.getName(), baos.toByteArray());
                }
                zis.closeEntry();
            }
        }

        // 2. Находим главный файл course.json в корне
        byte[] mainJsonBytes = files.get("course.json");
        if (mainJsonBytes == null) {
            throw new InvalidJsonException("В ZIP-архиве не найден файл course.json в корне");
        }
        CourseStructureImportDto courseDto = objectMapper.readValue(mainJsonBytes, CourseStructureImportDto.class);

        // 3. Создаём только курс (без разделов)
        Course course = courseImportService.createCourseOnly(courseDto);

        // 4. Определяем папки разделов (все поддиректории корня, имена которых состоят только из цифр)
        List<String> sectionDirs = new ArrayList<>();
        for (String path : files.keySet()) {
            if (path.contains("/")) {
                String rootDir = path.substring(0, path.indexOf('/'));
                if (SECTION_DIR_PATTERN.matcher(rootDir).matches()) {
                    if (!sectionDirs.contains(rootDir)) {
                        sectionDirs.add(rootDir);
                    }
                }
            }
        }
        // Сортируем папки разделов по числовому значению
        sectionDirs.sort(Comparator.comparingInt(Integer::parseInt));

        // 5. Получаем информацию о разделах из courseDto (для названий и описаний)
        List<SectionStructureImportDto> sectionInfos = courseDto.getSections();
        if (sectionInfos == null) {
            sectionInfos = Collections.emptyList();
        }

        // 6. Для каждой папки раздела создаём раздел и обрабатываем темы
        int sectionIndex = 0;
        for (String sectionDir : sectionDirs) {
            int sectionNum = Integer.parseInt(sectionDir);
            String sectionTitle;
            String sectionDesc;
            if (sectionIndex < sectionInfos.size()) {
                SectionStructureImportDto info = sectionInfos.get(sectionIndex);
                sectionTitle = info.getTitle() != null ? info.getTitle() : "Раздел " + sectionNum;
                sectionDesc = info.getDescription() != null ? info.getDescription() : "";
            } else {
                sectionTitle = "Раздел " + sectionNum;
                sectionDesc = "";
            }

            // Создаём раздел
            Section section = new Section();
            section.setCourse(course);
            section.setTitle(sectionTitle);
            section.setDescription(sectionDesc);
            section.setOrderIndex(sectionIndex);
            section = sectionRepository.save(section);

            // Собираем все JSON-файлы тем внутри папки раздела
            List<String> topicJsonPaths = new ArrayList<>();
            for (String path : files.keySet()) {
                if (path.startsWith(sectionDir + "/") && path.endsWith(".json")) {
                    topicJsonPaths.add(path);
                }
            }
            topicJsonPaths.sort((p1, p2) -> {
                String f1 = p1.substring(p1.lastIndexOf('/') + 1);
                String f2 = p2.substring(p2.lastIndexOf('/') + 1);
                Matcher m1 = TOPIC_NUMBER_PATTERN.matcher(f1);
                Matcher m2 = TOPIC_NUMBER_PATTERN.matcher(f2);
                if (m1.matches() && m2.matches()) {
                    int sec1 = Integer.parseInt(m1.group(1));
                    int top1 = Integer.parseInt(m1.group(2));
                    int sec2 = Integer.parseInt(m2.group(1));
                    int top2 = Integer.parseInt(m2.group(2));
                    if (sec1 != sec2) return Integer.compare(sec1, sec2);
                    return Integer.compare(top1, top2);
                }
                return 0;
            });
            // Сортируем файлы тем по числовому номеру (извлекаем из имени файла, например 1.1.json -> номер 1)
            topicJsonPaths.sort(Comparator.comparingInt(path -> {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                Matcher m = TOPIC_NUMBER_PATTERN.matcher(fileName);
                if (m.matches()) {
                    return Integer.parseInt(m.group(1));
                }
                return 0;
            }));

            // Для каждого файла темы импортируем тему
            int topicOrder = 0;
            for (String topicPath : topicJsonPaths) {
                byte[] topicBytes = files.get(topicPath);
                TopicImportDto topicDto = objectMapper.readValue(topicBytes, TopicImportDto.class);

                // Создаём тему в БД (временно без блоков, но с ID)
                Topic topic = new Topic();
                topic.setSection(section);
                topic.setTitle(topicDto.getTitle());
                topic.setDescription(topicDto.getDescription());
                topic.setOrderIndex(topicOrder++);
                topic = topicRepository.save(topic);

                // Проверяем наличие папки с изображениями: имя папки = имя JSON-файла без расширения
                String topicBaseName = topicPath.substring(topicPath.lastIndexOf('/') + 1);
                topicBaseName = topicBaseName.substring(0, topicBaseName.lastIndexOf('.'));
                String imageFolderPath = sectionDir + "/" + topicBaseName + "/";
                Map<String, byte[]> imageFiles = new HashMap<>();
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    if (entry.getKey().startsWith(imageFolderPath) && !entry.getKey().endsWith("/")) {
                        String imageName = entry.getKey().substring(imageFolderPath.length());
                        imageFiles.put(imageName, entry.getValue());
                    }
                }

                // Импортируем тему с блоками, вопросами и изображениями
                courseImportService.importTopicWithImages(topicDto, topic, imageFiles);
            }
            sectionIndex++;
        }

        return course;
    }
}