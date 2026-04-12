package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.dto.*;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.lbt.telegram_learning_bot.util.Constants.ENTITY_BLOCK;
import static com.lbt.telegram_learning_bot.util.Constants.ENTITY_QUESTION;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseImportService {

    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final TopicRepository topicRepository;
    private final BlockRepository blockRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final BlockImageRepository blockImageRepository;
    private final QuestionImageRepository questionImageRepository;
    private final ObjectMapper objectMapper;
    private final UserProgressRepository userProgressRepository;

    @Value("${course.images.storage.path:./course_images}")
    private String imageStoragePath;

    // ================== Импорт полного курса из JSON ==================

    @Transactional
    public Course importCourse(InputStream jsonStream) throws IOException {
        CourseImportDto dto = objectMapper.readValue(jsonStream, CourseImportDto.class);
        return importCourse(dto);
    }

    @Transactional
    public Course importCourse(CourseImportDto dto) {
        validateCourseImportDto(dto);
        Course course = new Course();
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course = courseRepository.save(course);

        int sectionOrder = 0;
        for (SectionImportDto secDto : dto.getSections()) {
            Section section = new Section();
            section.setCourse(course);
            section.setTitle(secDto.getTitle());
            section.setDescription(secDto.getDescription());
            section.setOrderIndex(sectionOrder++);
            section = sectionRepository.save(section);

            int topicOrder = 0;
            for (TopicImportDto topicDto : secDto.getTopics()) {
                Topic topic = new Topic();
                topic.setSection(section);
                topic.setTitle(topicDto.getTitle());
                topic.setDescription(topicDto.getDescription());
                topic.setOrderIndex(topicOrder++);
                topic = topicRepository.save(topic);
                createBlocksForTopic(topic, topicDto.getBlocks(), null);
            }
        }
        return course;
    }

    // ================== Импорт только структуры курса ==================

    @Transactional
    public Course importCourseStructure(CourseStructureImportDto dto) {
        validateCourseStructureDto(dto);
        Course course = new Course();
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course = courseRepository.save(course);

        if (dto.getSections() != null) {
            int sectionOrder = 0;
            for (SectionStructureImportDto secDto : dto.getSections()) {
                Section section = new Section();
                section.setCourse(course);
                section.setTitle(secDto.getTitle());
                section.setDescription(secDto.getDescription());
                section.setOrderIndex(sectionOrder++);
                section = sectionRepository.save(section);

                int topicOrder = 0;
                for (TopicStructureImportDto topicDto : secDto.getTopics()) {
                    Topic topic = new Topic();
                    topic.setSection(section);
                    topic.setTitle(topicDto.getTitle());
                    topic.setDescription(topicDto.getDescription());
                    topic.setOrderIndex(topicOrder++);
                    topicRepository.save(topic);
                }
            }
        }
        return course;
    }

    // ================== Точечное обновление раздела (полная замена) ==================

    @Transactional
    public Section importSection(SectionImportDto dto, Section existingSection) {
        validateSectionImportDto(dto);

        // Удаляем все записи прогресса, связанные с вопросами раздела
        List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(existingSection.getId());
        for (Topic topic : topics) {
            List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId());
            for (Block block : blocks) {
                List<Question> questions = questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId());
                for (Question question : questions) {
                    userProgressRepository.deleteByQuestionId(question.getId());
                }
            }
        }

        // Удаляем старый раздел (каскадно удалятся темы, блоки, вопросы, изображения)
        sectionRepository.delete(existingSection);

        // Создаём новый раздел
        Section newSection = new Section();
        newSection.setCourse(existingSection.getCourse());
        newSection.setTitle(dto.getTitle());
        newSection.setDescription(dto.getDescription());
        newSection.setOrderIndex(existingSection.getOrderIndex());
        newSection = sectionRepository.save(newSection);

        int topicOrder = 0;
        for (TopicImportDto topicDto : dto.getTopics()) {
            Topic topic = new Topic();
            topic.setSection(newSection);
            topic.setTitle(topicDto.getTitle());
            topic.setDescription(topicDto.getDescription());
            topic.setOrderIndex(topicOrder++);
            topic = topicRepository.save(topic);
            createBlocksForTopic(topic, topicDto.getBlocks(), null);
        }

        return newSection;
    }

    // ================== Точечное обновление темы (полная замена) ==================

    @Transactional
    public Topic importTopic(TopicImportDto dto, Topic existingTopic) {
        return importTopicWithImages(dto, existingTopic, null);
    }

    @Transactional
    public Topic importTopicWithImages(TopicImportDto dto, Topic existingTopic, Map<String, byte[]> imageFiles) {
        validateTopicImportDto(dto);

        // Удаляем старые записи прогресса
        List<Block> oldBlocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(existingTopic.getId());
        for (Block block : oldBlocks) {
            List<Question> questions = questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId());
            for (Question question : questions) {
                userProgressRepository.deleteByQuestionId(question.getId());
            }
        }

        // Удаляем старую тему (каскадно)
        topicRepository.delete(existingTopic);

        // Создаём новую тему
        Topic newTopic = new Topic();
        newTopic.setSection(existingTopic.getSection());
        newTopic.setTitle(dto.getTitle());
        newTopic.setDescription(dto.getDescription());
        newTopic.setOrderIndex(existingTopic.getOrderIndex());
        newTopic = topicRepository.save(newTopic);

        // Импортируем блоки с возможными изображениями
        createBlocksForTopic(newTopic, dto.getBlocks(), imageFiles);

        return newTopic;
    }

    // ================== Создание блоков с вопросами и изображениями ==================

    private void createBlocksForTopic(Topic topic, List<BlockImportDto> blockDtos, Map<String, byte[]> imageFiles) {
        if (blockDtos == null) return;

        // Для простоты: все изображения из папки темы собираем в плоский список,
        // и при создании каждого изображения блока/вопроса берём следующий файл по порядку.
        // Если в DTO для блока/вопроса есть описание, оно используется, но файл всё равно берётся по порядку.
        // Если файлов меньше, чем описаний, оставляем пустые.
        List<Map.Entry<String, byte[]>> sortedImages = null;
        if (imageFiles != null && !imageFiles.isEmpty()) {
            sortedImages = new ArrayList<>(imageFiles.entrySet());
            // Сортируем по имени файла для детерминированного порядка
            sortedImages.sort(Map.Entry.comparingByKey());
        }

        int blockOrder = 0;
        int imageIndex = 0;
        for (BlockImportDto blockDto : blockDtos) {
            Block block = new Block();
            block.setTopic(topic);
            block.setTextContent(blockDto.getText());
            block.setOrderIndex(blockOrder++);
            block = blockRepository.save(block);

            // Изображения блока
            if (blockDto.getImages() != null) {
                int imgOrder = 0;
                for (String imageDesc : blockDto.getImages()) {
                    BlockImage blockImage = new BlockImage();
                    blockImage.setBlock(block);
                    blockImage.setDescription(imageDesc);
                    blockImage.setOrderIndex(imgOrder++);
                    // Если есть файлы из папки, привязываем следующий
                    if (sortedImages != null && imageIndex < sortedImages.size()) {
                        Map.Entry<String, byte[]> imgEntry = sortedImages.get(imageIndex);
                        String savedPath = saveImageFile(imgEntry.getValue(), topic.getSection().getCourse().getId(), topic.getId(), imgEntry.getKey());
                        blockImage.setFilePath(savedPath);
                        imageIndex++;
                    } else {
                        blockImage.setFilePath("");
                    }
                    blockImageRepository.save(blockImage);
                }
            }

            // Вопросы блока
            if (blockDto.getQuestions() != null) {
                int questionOrder = 0;
                for (QuestionImportDto qDto : blockDto.getQuestions()) {
                    Question question = new Question();
                    question.setBlock(block);
                    question.setText(qDto.getText());
                    question.setExplanation(qDto.getExplanation());
                    question.setOrderIndex(questionOrder++);
                    question = questionRepository.save(question);

                    // Варианты ответов
                    int optOrder = 0;
                    for (String optText : qDto.getOptions()) {
                        AnswerOption opt = new AnswerOption();
                        opt.setQuestion(question);
                        opt.setText(optText);
                        opt.setIsCorrect(optOrder == qDto.getCorrectIndex());
                        opt.setOrderIndex(optOrder++);
                        answerOptionRepository.save(opt);
                    }

                    // Изображения вопроса
                    if (qDto.getImages() != null) {
                        int imgOrder = 0;
                        for (String imgDesc : qDto.getImages()) {
                            QuestionImage qi = new QuestionImage();
                            qi.setQuestion(question);
                            qi.setDescription(imgDesc);
                            qi.setOrderIndex(imgOrder++);
                            if (sortedImages != null && imageIndex < sortedImages.size()) {
                                Map.Entry<String, byte[]> imgEntry = sortedImages.get(imageIndex);
                                String savedPath = saveImageFile(imgEntry.getValue(), topic.getSection().getCourse().getId(), topic.getId(), imgEntry.getKey());
                                qi.setFilePath(savedPath);
                                imageIndex++;
                            } else {
                                qi.setFilePath("");
                            }
                            questionImageRepository.save(qi);
                        }
                    }
                }
            }
        }
    }

    // ================== Сохранение изображения на диск ==================

    private String saveImageFile(byte[] data, Long courseId, Long topicId, String originalFileName) {
        try {
            Path courseDir = Paths.get(imageStoragePath, String.valueOf(courseId));
            Path topicDir = courseDir.resolve(String.valueOf(topicId));
            if (!Files.exists(topicDir)) {
                Files.createDirectories(topicDir);
            }
            // Генерируем уникальное имя, чтобы избежать коллизий
            String ext = "";
            if (originalFileName.contains(".")) {
                ext = originalFileName.substring(originalFileName.lastIndexOf('.'));
            }
            String fileName = UUID.randomUUID() + ext;
            Path targetPath = topicDir.resolve(fileName);
            Files.write(targetPath, data);
            return targetPath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Failed to save image for course {} topic {}", courseId, topicId, e);
            return "";
        }
    }

    // ================== Сбор списка ожидающих изображений ==================

    public List<PendingImage> collectCourseImages(Long courseId) {
        List<PendingImage> result = new ArrayList<>();
        for (Section section : sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId)) {
            for (Topic topic : topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId())) {
                for (Block block : blockRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId())) {
                    for (BlockImage img : blockImageRepository.findByBlockIdOrderByOrderIndexAsc(block.getId())) {
                        if (img.getFilePath() == null || img.getFilePath().isEmpty()) {
                            result.add(new PendingImage(ENTITY_BLOCK, img.getId(), img.getDescription()));
                        }
                    }
                    for (Question question : questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId())) {
                        for (QuestionImage img : questionImageRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId())) {
                            if (img.getFilePath() == null || img.getFilePath().isEmpty()) {
                                result.add(new PendingImage(ENTITY_QUESTION, img.getId(), img.getDescription()));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    // ================== Обновление названия/описания курса и раздела ==================

    @Transactional
    public Course updateCourseNameDesc(Long courseId, String title, String description) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (title != null && !title.isEmpty()) course.setTitle(title);
        if (description != null) course.setDescription(description);
        return courseRepository.save(course);
    }

    @Transactional
    public Section updateSectionNameDesc(Long sectionId, String title, String description) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));
        if (title != null && !title.isEmpty()) section.setTitle(title);
        if (description != null) section.setDescription(description);
        return sectionRepository.save(section);
    }

    // ================== Валидации ==================

    private void validateCourseImportDto(CourseImportDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            errors.add("Название курса не может быть пустым");
        if (dto.getDescription() == null || dto.getDescription().trim().isEmpty())
            errors.add("Описание курса не может быть пустым");
        if (dto.getSections() == null || dto.getSections().isEmpty())
            errors.add("Курс должен содержать хотя бы один раздел");
        // ... (остальная валидация аналогична существующей)
        if (!errors.isEmpty())
            throw new InvalidJsonException("Ошибки в JSON:\n" + String.join("\n", errors));
    }

    private void validateCourseStructureDto(CourseStructureImportDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            errors.add("Название курса не может быть пустым");
        if (dto.getSections() != null) {
            for (int i = 0; i < dto.getSections().size(); i++) {
                SectionStructureImportDto sec = dto.getSections().get(i);
                if (sec.getTitle() == null || sec.getTitle().trim().isEmpty())
                    errors.add("Раздел " + (i+1) + ": название не может быть пустым");
                if (sec.getTopics() == null || sec.getTopics().isEmpty())
                    errors.add("Раздел " + (i+1) + ": должен содержать хотя бы одну тему");
                for (int j = 0; j < sec.getTopics().size(); j++) {
                    TopicStructureImportDto topic = sec.getTopics().get(j);
                    if (topic.getTitle() == null || topic.getTitle().trim().isEmpty())
                        errors.add("Раздел " + (i+1) + ", тема " + (j+1) + ": название не может быть пустым");
                }
            }
        } else {
            errors.add("Структура курса должна содержать список разделов");
        }
        if (!errors.isEmpty())
            throw new InvalidJsonException("Ошибки в JSON структуры:\n" + String.join("\n", errors));
    }

    private void validateSectionImportDto(SectionImportDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            errors.add("Название раздела не может быть пустым");
        if (dto.getTopics() == null || dto.getTopics().isEmpty())
            errors.add("Раздел должен содержать хотя бы одну тему");
        // Можно добавить более глубокую валидацию тем, но она будет выполнена в validateTopicImportDto
        if (!errors.isEmpty())
            throw new InvalidJsonException("Ошибки в JSON раздела:\n" + String.join("\n", errors));
    }

    private void validateTopicImportDto(TopicImportDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            errors.add("Название темы не может быть пустым");
        if (dto.getBlocks() == null || dto.getBlocks().isEmpty())
            errors.add("Тема должна содержать хотя бы один блок");
        // ... (остальная валидация блоков и вопросов, как в существующем коде)
        if (!errors.isEmpty())
            throw new InvalidJsonException("Ошибки в JSON темы:\n" + String.join("\n", errors));
    }

    // CourseImportService.java
    @Transactional
    public Course createCourseOnly(CourseStructureImportDto dto) {
        validateCourseStructureDto(dto); // валидация структуры (но разделы и темы не создаются)
        Course course = new Course();
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        return courseRepository.save(course);
    }
}