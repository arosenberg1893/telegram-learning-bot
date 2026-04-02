package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final TopicRepository topicRepository;
    private final BlockRepository blockRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserTestResultRepository userTestResultRepository;
    private static final int SESSION_TIMEOUT_SECONDS = 300;
    private final UserStudyTimeRepository userStudyTimeRepository;

    public String getCourseDescription(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    /**
     * Фиксирует активное действие пользователя в теме.
     * Если с последнего действия прошло меньше SESSION_TIMEOUT_SECONDS,
     * то разница добавляется к общему времени.
     */
    @Transactional
    public void recordStudyAction(Long userId, Long topicId) {
        Instant now = Instant.now();
        UserStudyTime studyTime = userStudyTimeRepository.findByUserIdAndTopicId(userId, topicId)
                .orElseGet(() -> {
                    UserStudyTime newTime = new UserStudyTime();
                    newTime.setUserId(userId);
                    newTime.setTopic(topicRepository.getReferenceById(topicId));
                    newTime.setTotalSeconds(0);
                    newTime.setLastActionAt(now);
                    return newTime;
                });

        if (studyTime.getLastActionAt() != null) {
            long secondsSinceLast = Duration.between(studyTime.getLastActionAt(), now).getSeconds();
            if (secondsSinceLast > 0 && secondsSinceLast < SESSION_TIMEOUT_SECONDS) {
                studyTime.setTotalSeconds(studyTime.getTotalSeconds() + (int) secondsSinceLast);
            }
        }
        studyTime.setLastActionAt(now);
        userStudyTimeRepository.save(studyTime);
    }

    /**
     * Суммарное время изучения пользователем всех тем.
     */
    public long getTotalStudySecondsForUser(Long userId) {
        Long sum = userStudyTimeRepository.sumTotalSecondsByUserId(userId);
        return sum == null ? 0 : sum;
    }

    /**
     * Суммарное время изучения по курсу.
     */
    public long getStudySecondsForCourse(Long userId, Long courseId) {
        List<Long> topicIds = getAllTopicIdsForCourse(courseId);
        return userStudyTimeRepository.findByUserIdAndTopicIdIn(userId, topicIds)
                .stream().mapToLong(UserStudyTime::getTotalSeconds).sum();
    }

    /**
     * Суммарное время изучения по разделу.
     */
    public long getStudySecondsForSection(Long userId, Long sectionId) {
        List<Long> topicIds = topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId)
                .stream().map(Topic::getId).toList();
        return userStudyTimeRepository.findByUserIdAndTopicIdIn(userId, topicIds)
                .stream().mapToLong(UserStudyTime::getTotalSeconds).sum();
    }

    // Вспомогательные методы
    private List<Long> getAllTopicIdsForCourse(Long courseId) {
        return sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .flatMap(s -> topicRepository.findBySectionIdOrderByOrderIndexAsc(s.getId()).stream())
                .map(Topic::getId).toList();
    }

    public String getSectionDescription(Long sectionId) {
        return sectionRepository.findById(sectionId).map(Section::getDescription).orElse("");
    }

    public Optional<Block> getBlockWithImages(Long blockId) {
        return blockRepository.findByIdWithImages(blockId);
    }

    @Transactional(readOnly = true)
    public Optional<Question> getQuestionWithImagesAndOptions(Long questionId) {
        Optional<Question> opt = questionRepository.findById(questionId);
        opt.ifPresent(q -> {
            // Инициализируем коллекции в рамках транзакции
            q.getImages().size();
            q.getAnswerOptions().size();
        });
        return opt;
    }

    /**
     * Количество курсов, которые пользователь начал (есть прогресс)
     */
    @Transactional(readOnly = true)
    public long getTotalStartedCourses(Long userId) {
        return userProgressRepository.countDistinctCoursesByUserId(userId);
    }

    /**
     * Количество курсов, полностью пройденных пользователем.
     * Курс считается полностью пройденным, если пользователь ответил на все вопросы курса.
     */
    @Transactional(readOnly = true)
    public long getCompletedCoursesCount(Long userId) {
        // Получаем все курсы, где есть прогресс пользователя
        List<Long> courseIds = userProgressRepository.findDistinctCourseIdsWithProgressByUserId(userId);
        long completed = 0;
        for (Long courseId : courseIds) {
            long totalQuestions = courseRepository.countQuestionsByCourseId(courseId);
            long answeredQuestions = userProgressRepository.countDistinctAnsweredQuestionsByUserAndCourse(userId, courseId);
            if (totalQuestions > 0 && answeredQuestions >= totalQuestions) {
                completed++;
            }
        }
        return completed;
    }

    /**
     * Самый трудный курс – курс с наибольшим процентом неправильных ответов.
     * Возвращает название курса и процент ошибок, либо null, если нет данных.
     */
    @Transactional(readOnly = true)
    public String getHardestCourse(Long userId) {
        List<Object[]> stats = userProgressRepository.getQuestionStatsByUserWithTitle(userId);
        if (stats.isEmpty()) {
            return MSG_NO_DATA;
        }

        Object[] hardest = null;
        double maxErrorRate = -1.0;
        for (Object[] row : stats) {
            Long courseId = (Long) row[0]; // не используется, но можно
            String title = (String) row[1];
            long totalAnswers = (Long) row[2];
            long wrongAnswers = (Long) row[3];
            double errorRate = (double) wrongAnswers / totalAnswers;
            if (errorRate > maxErrorRate) {
                maxErrorRate = errorRate;
                hardest = row;
            }
        }

        if (hardest == null) {
            return MSG_NO_DATA;
        }

        String courseTitle = (String) hardest[1];
        int percent = (int) Math.round(maxErrorRate * 100);
        return String.format("%s (%d%% ошибок)", courseTitle, percent);
    }

    public String getTopicStatusEmoji(Long userId, Long topicId) {
        List<Question> questions = getAllQuestionsForTopic(topicId);
        if (questions.isEmpty()) return EMOJI_NOT_STARTED;

        int total = questions.size();
        int answered = 0;
        int correct = 0;

        for (Question q : questions) {
            Optional<UserProgress> lastProgress = userProgressRepository
                    .findTopByUserIdAndQuestionIdAndModeOrderByCompletedAtDesc(userId, q.getId(), MODE_LEARNING);
            if (lastProgress.isPresent()) {
                answered++;
                if (lastProgress.get().getAnswerResult()) {
                    correct++;
                }
            }
        }

        if (answered == 0) return EMOJI_NOT_STARTED;
        if (answered < total) return EMOJI_IN_PROGRESS;
        if (correct == total) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    public String getSectionStatusEmoji(Long userId, Long sectionId) {
        List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);
        if (topics.isEmpty()) return EMOJI_NOT_STARTED;

        boolean anyLearning = false; // есть ли хоть один учебный ответ в разделе
        boolean anyNotGreen = false; // есть ли что-то не зелёное (учебное или тест)
        boolean allTopicsGreen = true; // все темы, имеющие вопросы, зелёные по учебному статусу

        for (Topic topic : topics) {
            long totalQuestions = questionRepository.countByTopicId(topic.getId());
            if (totalQuestions == 0) continue; // тема без вопросов игнорируется

            String learningStatus = getTopicLearningStatus(userId, topic.getId());
            if (!learningStatus.equals(EMOJI_NOT_STARTED)) anyLearning = true;
            if (!learningStatus.equals(EMOJI_COMPLETED)) allTopicsGreen = false;
            if (!learningStatus.equals(EMOJI_COMPLETED) && !learningStatus.equals(EMOJI_NOT_STARTED))
                anyNotGreen = true;
        }

        // Получаем статус итогового теста раздела
        String testStatus = getSectionTestStatus(userId, sectionId);

        if (!anyLearning && testStatus.equals(EMOJI_NOT_STARTED)) return EMOJI_NOT_STARTED;
        if (allTopicsGreen && testStatus.equals(EMOJI_COMPLETED)) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    public String getCourseStatusEmoji(Long userId, Long courseId) {
        List<Section> sections = sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
        if (sections.isEmpty()) return EMOJI_NOT_STARTED;

        boolean anyLearning = false;
        boolean anyNotGreen = false;
        boolean allSectionsGreen = true;

        for (Section section : sections) {
            // Проверяем, есть ли в секции хоть одна тема с вопросами
            List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId());
            boolean sectionHasQuestions = topics.stream()
                    .anyMatch(t -> questionRepository.countByTopicId(t.getId()) > 0);
            if (!sectionHasQuestions) continue;

            String sectionStatus = getSectionStatusEmoji(userId, section.getId());
            if (!sectionStatus.equals(EMOJI_NOT_STARTED)) anyLearning = true;
            if (!sectionStatus.equals(EMOJI_COMPLETED)) allSectionsGreen = false;
            if (!sectionStatus.equals(EMOJI_COMPLETED) && !sectionStatus.equals(EMOJI_NOT_STARTED)) anyNotGreen = true;
        }

        String courseTestStatus = getCourseTestStatusInternal(userId, courseId);

        if (!anyLearning && courseTestStatus.equals(EMOJI_NOT_STARTED)) return EMOJI_NOT_STARTED;
        if (allSectionsGreen && courseTestStatus.equals(EMOJI_COMPLETED)) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    /**
     * Вычисляет статус прохождения теста по результату.
     * Единый метод для всех типов тестов.
     */
    private String computeTestStatusEmoji(Optional<UserTestResult> resultOpt) {
        if (resultOpt.isEmpty()) return EMOJI_NOT_STARTED;
        UserTestResult r = resultOpt.get();
        int total = r.getCorrectCount() + r.getWrongCount();
        if (total == 0) return EMOJI_NOT_STARTED;
        double percent = (double) r.getCorrectCount() / total;
        if (percent >= 1.0) return EMOJI_COMPLETED;
        if (percent >= 0.5) return EMOJI_IN_PROGRESS;
        return EMOJI_FAILED;
    }

    public String getTopicTestStatus(Long userId, Long topicId) {
        return computeTestStatusEmoji(
            userTestResultRepository.findByUserIdAndTestTypeAndTestId(userId, TEST_TYPE_TOPIC, topicId));
    }

    public String getSectionTestStatus(Long userId, Long sectionId) {
        return computeTestStatusEmoji(
            userTestResultRepository.findByUserIdAndTestTypeAndTestId(userId, TEST_TYPE_SECTION, sectionId));
    }

    public String getCourseTestStatus(Long userId, Long courseId) {
        return computeTestStatusEmoji(
            userTestResultRepository.findByUserIdAndTestTypeAndTestId(userId, TEST_TYPE_COURSE, courseId));
    }

    private String getCourseTestStatusInternal(Long userId, Long courseId) {
        return getCourseTestStatus(userId, courseId);
    }

    private String getTopicLearningStatus(Long userId, Long topicId) {
        List<Question> questions = getAllQuestionsForTopic(topicId);
        if (questions.isEmpty()) return EMOJI_NOT_STARTED;

        int total = questions.size();
        int answered = 0;
        int correct = 0;

        for (Question q : questions) {
            Optional<UserProgress> lastProgress = userProgressRepository
                    .findTopByUserIdAndQuestionIdAndModeOrderByCompletedAtDesc(userId, q.getId(), MODE_LEARNING);
            if (lastProgress.isPresent()) {
                answered++;
                if (lastProgress.get().getAnswerResult()) {
                    correct++;
                }
            }
        }

        if (answered == 0) return EMOJI_NOT_STARTED;
        if (correct == total) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    @Transactional
    public void saveTestResult(Long userId, String testType, Long testId, int correct, int wrong) {
        UserTestResult result = userTestResultRepository
                .findByUserIdAndTestTypeAndTestId(userId, testType, testId)
                .orElse(new UserTestResult());
        result.setUserId(userId);
        result.setTestType(testType);
        result.setTestId(testId);
        result.setCorrectCount(correct);
        result.setWrongCount(wrong);
        userTestResultRepository.save(result);
    }

    private String prepareFullTextQuery(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return "";
        }
        // Удаляем лишние символы, оставляем буквы, цифры и пробелы
        String cleaned = userInput.replaceAll("[^\\p{L}\\p{N}\\s]+", "").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        // Разбиваем на слова и соединяем оператором AND (&)
        String[] words = cleaned.split("\\s+");
        return String.join(" & ", words);
    }

    @Transactional(readOnly = true)
    public Optional<Block> getNextBlock(Long currentBlockId, Long topicId) {
        // Находим текущий блок
        Block current = blockRepository.findById(currentBlockId).orElse(null);
        if (current == null) return Optional.empty();

        // Получаем все блоки темы, отсортированные по orderIndex
        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
        int currentIndex = blocks.indexOf(current);
        if (currentIndex >= 0 && currentIndex < blocks.size() - 1) {
            return Optional.of(blocks.get(currentIndex + 1));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<Block> getPrevBlock(Long currentBlockId, Long topicId) {
        Block current = blockRepository.findById(currentBlockId).orElse(null);
        if (current == null) return Optional.empty();

        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
        int currentIndex = blocks.indexOf(current);
        if (currentIndex > 0) {
            return Optional.of(blocks.get(currentIndex - 1));
        }
        return Optional.empty();
    }

    @Transactional
    public void saveAnswerProgress(Long userId, Long questionId, boolean correct, boolean isLearning) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        UserProgress progress = new UserProgress();
        progress.setUserId(userId);
        progress.setCourse(question.getBlock().getTopic().getSection().getCourse());
        progress.setQuestion(question);
        progress.setIsPassed(true);
        progress.setAnswerResult(correct);
        progress.setCompletedAt(Instant.now());
        progress.setMode(isLearning ? MODE_LEARNING : MODE_TEST);
        userProgressRepository.save(progress);
        updateCourseLastAccessed(userId, progress.getCourse().getId());
    }

    @Transactional
    public void updateCourseLastAccessed(Long userId, Long courseId) {
        if (courseId == null) {
            log.warn("Course id is null, skipping last accessed update");
            return;
        }
        if (!courseRepository.existsById(courseId)) {
            log.warn("Course with id {} does not exist, skipping last accessed update", courseId);
            return;
        }
        List<UserProgress> progressList = userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId);
        UserProgress progress = progressList.isEmpty() ? null : progressList.get(0);
        if (progress == null) {
            progress = new UserProgress();
            progress.setUserId(userId);
            progress.setCourse(courseRepository.getReferenceById(courseId));
            progress.setIsPassed(false);
            progress.setMode(MODE_LEARNING);
        }
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    public PaginationResult<Course> getMyCoursesPage(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize); // убрана сортировка
        Page<Course> coursePage = userProgressRepository.findCoursesWithProgressOrderByLastAccessed(userId, pageable);
        return new PaginationResult<>(
                coursePage.getContent(),
                coursePage.getNumber(),
                coursePage.getTotalPages(),
                coursePage.getTotalElements(),
                coursePage.hasPrevious(),
                coursePage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public List<Block> getTopicBlocksWithQuestions(Long topicId) {
        return blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
    }

    // Для меню "Выбрать курс" (все курсы, алфавитная сортировка)
    public PaginationResult<Course> getAllCoursesPage(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("title").ascending());
        Page<Course> coursePage = courseRepository.findAll(pageable);
        return new PaginationResult<>(
                coursePage.getContent(),
                coursePage.getNumber(),
                coursePage.getTotalPages(),
                coursePage.getTotalElements(),
                coursePage.hasPrevious(),
                coursePage.hasNext()
        );
    }

    public String getLastAccessedTime(Long userId, Long courseId) {
        return userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId)
                .stream()
                .findFirst()
                .map(UserProgress::getLastAccessedAt)
                .map(this::formatRelativeTime)
                .orElse("");
    }


    public Instant getCourseLastAccessed(Long userId, Long courseId) {
        return userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId)
                .stream()
                .findFirst()
                .map(UserProgress::getLastAccessedAt)
                .orElse(null);
    }

    @Transactional
    public void updateSectionLastAccessedOnExit(Long userId, Long sectionId) {
        if (sectionId == null) return;
        Section section = sectionRepository.getReferenceById(sectionId);
        UserProgress progress = userProgressRepository
                .findByUserIdAndSection(userId, section)
                .orElseGet(() -> {
                    UserProgress newProgress = new UserProgress();
                    newProgress.setUserId(userId);
                    newProgress.setSection(section);
                    newProgress.setCourse(section.getCourse());
                    newProgress.setIsPassed(false);
                    newProgress.setMode(MODE_LEARNING);
                    return newProgress;
                });
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    @Transactional
    public void updateSectionLastAccessed(Long userId, Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found"));
        UserProgress progress = userProgressRepository
                .findByUserIdAndSection(userId, section)
                .orElse(new UserProgress());

        if (progress.getId() == null) {
            progress.setUserId(userId);
            progress.setSection(section);
            progress.setCourse(section.getCourse());
            progress.setIsPassed(false);
            progress.setMode(MODE_LEARNING);
        }
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    public Instant getSectionLastAccessed(Long userId, Long sectionId) {
        return userProgressRepository
                .findByUserIdAndSectionIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, sectionId)
                .stream()
                .findFirst()
                .map(UserProgress::getLastAccessedAt)
                .orElse(null);
    }

    private String formatRelativeTime(Instant instant) {
        if (instant == null) return "";
        Duration duration = Duration.between(instant, Instant.now());
        long seconds = duration.getSeconds();
        if (seconds < 0) return ""; // на всякий случай

        if (seconds < 60) return TIME_JUST_NOW;
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " мин назад";
        long hours = minutes / 60;
        if (hours < 24) return hours + " ч назад";
        long days = hours / 24;
        if (days < 7) return days + " дн назад";
        // более недели – показываем дату
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    // Для результатов поиска
    public PaginationResult<Course> getFoundCoursesPage(String query, int page, int pageSize) {
        String fullTextQuery = prepareFullTextQuery(query);
        if (fullTextQuery.isEmpty()) {
            return new PaginationResult<>(Collections.emptyList(), page, 0, 0, false, false);
        }

        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Course> coursePage = courseRepository.searchByFullText(fullTextQuery, pageable);
        return new PaginationResult<>(
                coursePage.getContent(),
                coursePage.getNumber(),
                coursePage.getTotalPages(),
                coursePage.getTotalElements(),
                coursePage.hasPrevious(),
                coursePage.hasNext()
        );
    }

    public List<String> getCoursesProgress(Long userId) {
        List<Course> courses = courseRepository.findAll();
        if (courses.isEmpty()) return Collections.emptyList();

        List<Long> courseIds = courses.stream().map(Course::getId).toList();

        Map<Long, Long> totalQuestionsMap = courseRepository.countQuestionsByCourseIds(courseIds);
        Map<Long, Long> answeredMap = userProgressRepository.countDistinctAnsweredQuestionsByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));

        List<String> result = new ArrayList<>();
        for (Course course : courses) {
            Long total = totalQuestionsMap.getOrDefault(course.getId(), 0L);
            if (total == 0) continue;
            Long answered = answeredMap.getOrDefault(course.getId(), 0L);
            int percent = (int) Math.round(answered * 100.0 / total);
            String statusEmoji = answered == 0 ? EMOJI_NOT_STARTED : (answered < total ? EMOJI_IN_PROGRESS : EMOJI_COMPLETED);
            result.add(String.format(FORMAT_COURSE_PROGRESS,
                    statusEmoji, course.getTitle(), percent, answered, total));
        }
        return result;
    }


    public String getCourseTitle(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getTitle).orElse("");
    }

    public String getSectionTitle(Long sectionId) {
        return sectionRepository.findById(sectionId).map(Section::getTitle).orElse("");
    }

    public String getTopicTitle(Long topicId) {
        return topicRepository.findById(topicId).map(Topic::getTitle).orElse("");
    }

    public PaginationResult<Section> getSectionsPage(Long courseId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("orderIndex").ascending());
        Page<Section> sectionPage = sectionRepository.findByCourseId(courseId, pageable);
        return new PaginationResult<>(
                sectionPage.getContent(),
                sectionPage.getNumber(),
                sectionPage.getTotalPages(),
                sectionPage.getTotalElements(),
                sectionPage.hasPrevious(),
                sectionPage.hasNext()
        );
    }

    public PaginationResult<Topic> getTopicsPage(Long sectionId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("orderIndex").ascending());
        Page<Topic> topicPage = topicRepository.findBySectionId(sectionId, pageable);
        return new PaginationResult<>(
                topicPage.getContent(),
                topicPage.getNumber(),
                topicPage.getTotalPages(),
                topicPage.getTotalElements(),
                topicPage.hasPrevious(),
                topicPage.hasNext()
        );
    }



    @Transactional(readOnly = true)
    public Optional<Block> getBlock(Long blockId) {
        return blockRepository.findById(blockId);
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsForBlock(Long blockId) {
        return questionRepository.findByBlockIdOrderByOrderIndexAsc(blockId);
    }

    @Transactional(readOnly = true)
    public Optional<Question> getQuestion(Long questionId) {
        return questionRepository.findById(questionId);
    }

    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswerOptionsForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
    }

    // Для тестов: получить все вопросы темы
    @Transactional(readOnly = true)
    public List<Question> getAllQuestionsForTopic(Long topicId) {
        // Находим все блоки темы, затем собираем вопросы
        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
        return blocks.stream()
                .flatMap(block -> questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId()).stream())
                .toList();
    }
    // В классе NavigationService

    /**
     * Получить случайные вопросы для раздела (по 2 из каждого блока всех тем раздела)
     */
    @Transactional(readOnly = true)
    public List<Question> getRandomQuestionsForSection(Long sectionId, int questionsPerBlock) {
        List<Question> result = new ArrayList<>();
        List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);
        for (Topic topic : topics) {
            List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId());
            for (Block block : blocks) {
                List<Question> blockQuestions = new ArrayList<>(questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId()));
                if (!blockQuestions.isEmpty()) {
                    Collections.shuffle(blockQuestions);
                    result.addAll(blockQuestions.stream().limit(questionsPerBlock).toList());
                }
            }
        }
        return result;
    }

    /**
     * Получить случайные вопросы для курса (по 2 из каждой темы всех разделов)
     */
    @Transactional(readOnly = true)
    public List<Question> getRandomQuestionsForCourse(Long courseId, int questionsPerTopic) {
        List<Question> result = new ArrayList<>();
        List<Section> sections = sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
        for (Section section : sections) {
            List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId());
            for (Topic topic : topics) {
                List<Question> topicQuestions = new ArrayList<>(getAllQuestionsForTopic(topic.getId()));
                if (!topicQuestions.isEmpty()) {
                    Collections.shuffle(topicQuestions);
                    result.addAll(topicQuestions.stream().limit(questionsPerTopic).toList());
                }
            }
        }
        return result;
    }

    @Transactional
    public void recordMistake(Long userId, Long questionId) {
        UserMistake mistake = userMistakeRepository.findByUserIdAndQuestionId(userId, questionId)
                .orElse(new UserMistake());
        mistake.setUserId(userId);
        Question question = questionRepository.getReferenceById(questionId);
        mistake.setQuestion(question);
        mistake.setLastMistakeAt(Instant.now());
        userMistakeRepository.save(mistake);
    }

    @Transactional
    public void clearMistake(Long userId, Long questionId) {
        userMistakeRepository.deleteByUserIdAndQuestionId(userId, questionId);
    }

    @Transactional(readOnly = true)
    public List<Question> getMistakeQuestions(Long userId) {
        return userMistakeRepository.findMistakeQuestionsByUserId(userId);
    }

    // Получение сущностей по ID
    public Optional<Section> getSection(Long sectionId) {
        return sectionRepository.findById(sectionId);
    }

    public Optional<Topic> getTopic(Long topicId) {
        return topicRepository.findById(topicId);
    }

    @Transactional
    public void updateCourseLastAccessedOnExit(Long userId, Long courseId) {
        if (courseId == null) return;
        
        // Проверяем, существует ли курс
        if (!courseRepository.existsById(courseId)) {
            log.warn("Course {} does not exist, skipping last accessed update", courseId);
            return;
        }List<UserProgress> progressList = userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId);
        UserProgress progress = progressList.isEmpty() ? null : progressList.get(0);
        if (progress == null) {
            progress = new UserProgress();
            progress.setUserId(userId);
            progress.setCourse(courseRepository.getReferenceById(courseId));
            progress.setIsPassed(false);
            progress.setMode(MODE_LEARNING);
        }
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    public Map<Long, String> getCourseStatusesForUser(Long userId, List<Long> courseIds) {
        if (courseIds.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> totalQuestionsMap = courseRepository.countQuestionsByCourseIds(courseIds);
        Map<Long, Long> answeredMap = userProgressRepository.countDistinctAnsweredQuestionsByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
        Map<Long, Long> correctMap = userProgressRepository.countCorrectLearningAnswersByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
        Map<Long, String> testStatusMap = getCourseTestStatusesForUser(userId, courseIds);

        Map<Long, String> result = new HashMap<>();
        for (Long courseId : courseIds) {
            Long total = totalQuestionsMap.getOrDefault(courseId, 0L);
            if (total == 0) {
                result.put(courseId, EMOJI_NOT_STARTED);
                continue;
            }
            Long answered = answeredMap.getOrDefault(courseId, 0L);
            Long correct = correctMap.getOrDefault(courseId, 0L);
            String testStatus = testStatusMap.getOrDefault(courseId, EMOJI_NOT_STARTED);

            if (answered == 0 && testStatus.equals(EMOJI_NOT_STARTED)) {
                result.put(courseId, EMOJI_NOT_STARTED);
            } else if (answered.equals(total) && correct.equals(total) && testStatus.equals(EMOJI_COMPLETED)) {
                result.put(courseId, EMOJI_COMPLETED);
            } else {
                result.put(courseId, EMOJI_IN_PROGRESS);
            }
        }
        return result;
    }

    public Map<Long, String> getCourseTestStatusesForUser(Long userId, List<Long> courseIds) {
        if (courseIds.isEmpty()) return Collections.emptyMap();
        List<Object[]> results = userTestResultRepository.findTestResultsByUserAndTestIds(userId, TEST_TYPE_COURSE, courseIds);
        Map<Long, String> map = new HashMap<>();
        for (Object[] row : results) {
            Long courseId = (Long) row[0];
            int correct = ((Number) row[1]).intValue();
            int wrong = ((Number) row[2]).intValue();
            int total = correct + wrong;
            if (total == 0) continue;
            double percent = (double) correct / total;
            if (percent >= 1.0) map.put(courseId, EMOJI_COMPLETED);
            else if (percent >= 0.5) map.put(courseId, EMOJI_IN_PROGRESS);
            else map.put(courseId, EMOJI_FAILED);
        }
        return map;
    }

}