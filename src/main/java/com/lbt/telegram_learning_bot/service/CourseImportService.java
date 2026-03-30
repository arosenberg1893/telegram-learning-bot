package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.dto.*;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.ENTITY_BLOCK;
import static com.lbt.telegram_learning_bot.util.Constants.ENTITY_QUESTION;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

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

    /**
     * Парсит JSON из InputStream и создаёт курс с полной структурой (без изображений).
     * Возвращает созданный курс или null в случае ошибки.
     */
    @Transactional
    public Course importCourse(InputStream jsonStream) throws IOException {
        CourseImportDto dto = objectMapper.readValue(jsonStream, CourseImportDto.class);
        return importCourse(dto);
    }

    @Transactional
    public Course updateCourseNameDesc(Long courseId, String title, String description) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (title != null && !title.isEmpty()) {
            course.setTitle(title);
        }
        if (description != null) {
            course.setDescription(description);
        }
        return courseRepository.save(course);
    }
    private void validateCourseImportDto(CourseImportDto dto) {
        List<String> errors = new ArrayList<>();

        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            errors.add("Название курса не может быть пустым");
        }
        if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
            errors.add("Описание курса не может быть пустым");
        }

        if (dto.getSections() != null) {
            for (int i = 0; i < dto.getSections().size(); i++) {
                SectionImportDto sec = dto.getSections().get(i);
                if (sec.getTitle() == null || sec.getTitle().trim().isEmpty()) {
                    errors.add("Раздел " + (i + 1) + ": название не может быть пустым");
                }
                if (sec.getTopics() != null) {
                    for (int j = 0; j < sec.getTopics().size(); j++) {
                        TopicImportDto topic = sec.getTopics().get(j);
                        if (topic.getTitle() == null || topic.getTitle().trim().isEmpty()) {
                            errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ": название не может быть пустым");
                        }
                        if (topic.getBlocks() != null) {
                            for (int k = 0; k < topic.getBlocks().size(); k++) {
                                BlockImportDto block = topic.getBlocks().get(k);
                                if (block.getText() == null || block.getText().trim().isEmpty()) {
                                    errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ": текст блока не может быть пустым");
                                }
                                if (block.getQuestions() != null) {
                                    for (int l = 0; l < block.getQuestions().size(); l++) {
                                        QuestionImportDto q = block.getQuestions().get(l);
                                        if (q.getText() == null || q.getText().trim().isEmpty()) {
                                            errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ", вопрос " + (l + 1) + ": текст вопроса не может быть пустым");
                                        }
                                        if (q.getOptions() == null || q.getOptions().size() < 2) {
                                            errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ", вопрос " + (l + 1) + ": должно быть минимум 2 варианта ответа");
                                        } else {
                                            if (q.getCorrectIndex() < 0 || q.getCorrectIndex() >= q.getOptions().size()) {
                                                errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ", вопрос " + (l + 1) + ": индекс правильного ответа должен быть от 0 до " + (q.getOptions().size() - 1));
                                            }
                                        }
                                        if (q.getExplanation() == null || q.getExplanation().trim().isEmpty()) {
                                            errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ", вопрос " + (l + 1) + ": пояснение не может быть пустым");
                                        }
                                    }
                                } else {
                                    errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ", блок " + (k + 1) + ": блок должен содержать хотя бы один вопрос");
                                }
                            }
                        } else {
                            errors.add("Раздел " + (i + 1) + ", тема " + (j + 1) + ": тема должна содержать хотя бы один блок");
                        }
                    }
                } else {
                    errors.add("Раздел " + (i + 1) + ": раздел должен содержать хотя бы одну тему");
                }
            }
        } else {
            errors.add("Курс должен содержать хотя бы один раздел");
        }

        if (!errors.isEmpty()) {
            throw new InvalidJsonException("Ошибки в JSON:\n" + String.join("\n", errors));
        }
    }
    public List<PendingImage> collectCourseImages(Long courseId) {
        List<PendingImage> result = new ArrayList<>();
        List<Section> sections = sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
        for (Section section : sections) {
            List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId());
            for (Topic topic : topics) {
                List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId());
                for (Block block : blocks) {
                    // Изображения блока
                    List<BlockImage> blockImages = blockImageRepository.findByBlockIdOrderByOrderIndexAsc(block.getId());
                    for (BlockImage img : blockImages) {
                        if (img.getFilePath() == null || img.getFilePath().isEmpty()) {
                            result.add(new PendingImage(ENTITY_BLOCK, img.getId(), img.getDescription()));
                        }
                    }
                    // Вопросы блока и их изображения
                    List<Question> questions = questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId());
                    for (Question question : questions) {
                        List<QuestionImage> questionImages = questionImageRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId());
                        for (QuestionImage img : questionImages) {
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
    @Transactional
    public Section updateSectionNameDesc(Long sectionId, String title, String description) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));
        if (title != null && !title.isEmpty()) {
            section.setTitle(title);
        }
        if (description != null) {
            section.setDescription(description);
        }
        return sectionRepository.save(section);
    }
    private void validateTopicImportDto(TopicImportDto dto) {
        List<String> errors = new ArrayList<>();

        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            errors.add("Название темы не может быть пустым");
        }
        if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
            errors.add("Описание темы не может быть пустым");
        }

        if (dto.getBlocks() != null) {
            for (int k = 0; k < dto.getBlocks().size(); k++) {
                BlockImportDto block = dto.getBlocks().get(k);
                if (block.getText() == null || block.getText().trim().isEmpty()) {
                    errors.add("Блок " + (k + 1) + ": текст блока не может быть пустым");
                }
                if (block.getQuestions() != null) {
                    for (int l = 0; l < block.getQuestions().size(); l++) {
                        QuestionImportDto q = block.getQuestions().get(l);
                        if (q.getText() == null || q.getText().trim().isEmpty()) {
                            errors.add("Блок " + (k + 1) + ", вопрос " + (l + 1) + ": текст вопроса не может быть пустым");
                        }
                        if (q.getOptions() == null || q.getOptions().size() < 2) {
                            errors.add("Блок " + (k + 1) + ", вопрос " + (l + 1) + ": должно быть минимум 2 варианта ответа");
                        } else {
                            if (q.getCorrectIndex() < 0 || q.getCorrectIndex() >= q.getOptions().size()) {
                                errors.add("Блок " + (k + 1) + ", вопрос " + (l + 1) + ": индекс правильного ответа должен быть от 0 до " + (q.getOptions().size() - 1));
                            }
                        }
                        if (q.getExplanation() == null || q.getExplanation().trim().isEmpty()) {
                            errors.add("Блок " + (k + 1) + ", вопрос " + (l + 1) + ": пояснение не может быть пустым");
                        }
                    }
                } else {
                    errors.add("Блок " + (k + 1) + ": блок должен содержать хотя бы один вопрос");
                }
            }
        } else {
            errors.add("Тема должна содержать хотя бы один блок");
        }

        if (!errors.isEmpty()) {
            throw new InvalidJsonException("Ошибки в JSON темы:\n" + String.join("\n", errors));
        }
    }
    @Transactional
    public Course importCourse(CourseImportDto dto) {
        validateCourseImportDto(dto); // <-- добавить
        // Создаём курс
        Course course = new Course();
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription());
        course = courseRepository.save(course);

        int sectionOrder = 0;
        if (dto.getSections() != null) {
            for (SectionImportDto secDto : dto.getSections()) {
                Section section = new Section();
                section.setCourse(course);
                section.setTitle(secDto.getTitle());
                section.setDescription(secDto.getDescription());
                section.setOrderIndex(sectionOrder++);
                section = sectionRepository.save(section);

                int topicOrder = 0;
                if (secDto.getTopics() != null) {
                    for (TopicImportDto topicDto : secDto.getTopics()) {
                        Topic topic = new Topic();
                        topic.setSection(section);
                        topic.setTitle(topicDto.getTitle());
                        topic.setDescription(topicDto.getDescription());
                        topic.setOrderIndex(topicOrder++);
                        topic = topicRepository.save(topic);

                        createBlocksForTopic(topic, topicDto.getBlocks());
                    }
                }
            }
        }
        return course;
    }

    /**
     * Создаёт блоки с вопросами и изображениями для указанной темы.
     * Вынесено для устранения дублирования между importCourse и importTopic.
     */
    private void createBlocksForTopic(Topic topic, java.util.List<BlockImportDto> blockDtos) {
        if (blockDtos == null) return;
        int blockOrder = 0;
        for (BlockImportDto blockDto : blockDtos) {
            Block block = new Block();
            block.setTopic(topic);
            block.setTextContent(blockDto.getText());
            block.setOrderIndex(blockOrder++);
            block = blockRepository.save(block);

            if (blockDto.getImages() != null) {
                int imgOrder = 0;
                for (String imageDesc : blockDto.getImages()) {
                    BlockImage blockImage = new BlockImage();
                    blockImage.setBlock(block);
                    blockImage.setDescription(imageDesc);
                    blockImage.setFilePath("");
                    blockImage.setOrderIndex(imgOrder++);
                    blockImageRepository.save(blockImage);
                }
            }

            if (blockDto.getQuestions() != null) {
                int questionOrder = 0;
                for (QuestionImportDto qDto : blockDto.getQuestions()) {
                    Question question = new Question();
                    question.setBlock(block);
                    question.setText(qDto.getText());
                    question.setExplanation(qDto.getExplanation());
                    question.setOrderIndex(questionOrder++);
                    question = questionRepository.save(question);

                    int optOrder = 0;
                    for (String optText : qDto.getOptions()) {
                        AnswerOption opt = new AnswerOption();
                        opt.setQuestion(question);
                        opt.setText(optText);
                        opt.setIsCorrect(optOrder == qDto.getCorrectIndex());
                        opt.setOrderIndex(optOrder++);
                        answerOptionRepository.save(opt);
                    }

                    if (qDto.getImages() != null) {
                        int imgOrder = 0;
                        for (String imgDesc : qDto.getImages()) {
                            QuestionImage qi = new QuestionImage();
                            qi.setQuestion(question);
                            qi.setDescription(imgDesc);
                            qi.setFilePath("");
                            qi.setOrderIndex(imgOrder++);
                            questionImageRepository.save(qi);
                        }
                    }
                }
            }
        }
    }

    // Метод для обновления темы (для редактирования темы)
    @Transactional
    public Topic importTopic(TopicImportDto dto, Topic existingTopic) {
        validateTopicImportDto(dto);

        // Удаляем все записи прогресса, связанные с вопросами темы
        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(existingTopic.getId());
        for (Block block : blocks) {
            List<Question> questions = questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId());
            for (Question question : questions) {
                userProgressRepository.deleteByQuestionId(question.getId());
            }
        }

        // Теперь можно безопасно удалить тему (каскадно удалятся блоки, вопросы, изображения)
        topicRepository.delete(existingTopic);

        // Создаём новую тему
        Topic newTopic = new Topic();
        newTopic.setSection(existingTopic.getSection());
        newTopic.setTitle(dto.getTitle());
        newTopic.setDescription(dto.getDescription());
        newTopic.setOrderIndex(existingTopic.getOrderIndex());
        newTopic = topicRepository.save(newTopic);

        createBlocksForTopic(newTopic, dto.getBlocks());
        return newTopic;
    }
}