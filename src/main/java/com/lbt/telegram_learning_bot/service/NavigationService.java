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
    private final UserStudyTimeRepository userStudyTimeRepository;

    /** Максимальная пауза между действиями, засчитываемая как активная учёба (сек). */
    private static final int SESSION_TIMEOUT_SECONDS = 300;

    // ================== Время и статистика ==================

    @Transactional
    public void recordStudyAction(Long userId, Long topicId) {
        Instant now = Instant.now();
        UserStudyTime studyTime = userStudyTimeRepository
                .findByUserIdAndTopicId(userId, topicId)
                .orElseGet(() -> {
                    UserStudyTime t = new UserStudyTime();
                    t.setUserId(userId);
                    t.setTopic(topicRepository.getReferenceById(topicId));
                    t.setTotalSeconds(0);
                    t.setLastActionAt(now);
                    return t;
                });

        if (studyTime.getLastActionAt() != null) {
            long elapsed = Duration.between(studyTime.getLastActionAt(), now).getSeconds();
            if (elapsed > 0 && elapsed < SESSION_TIMEOUT_SECONDS) {
                studyTime.setTotalSeconds(studyTime.getTotalSeconds() + (int) elapsed);
            }
        }
        studyTime.setLastActionAt(now);
        userStudyTimeRepository.save(studyTime);
    }

    public long getTotalStudySecondsForUser(Long userId) {
        Long sum = userStudyTimeRepository.sumTotalSecondsByUserId(userId);
        return sum != null ? sum : 0L;
    }

    public long getStudySecondsForCourse(Long userId, Long courseId) {
        List<Long> topicIds = getAllTopicIdsForCourse(courseId);
        return userStudyTimeRepository.findByUserIdAndTopicIdIn(userId, topicIds)
                .stream().mapToLong(UserStudyTime::getTotalSeconds).sum();
    }

    public long getStudySecondsForSection(Long userId, Long sectionId) {
        List<Long> topicIds = topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId)
                .stream().map(Topic::getId).toList();
        return userStudyTimeRepository.findByUserIdAndTopicIdIn(userId, topicIds)
                .stream().mapToLong(UserStudyTime::getTotalSeconds).sum();
    }

    private List<Long> getAllTopicIdsForCourse(Long courseId) {
        return sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .flatMap(s -> topicRepository.findBySectionIdOrderByOrderIndexAsc(s.getId()).stream())
                .map(Topic::getId)
                .toList();
    }

    // ================== Получение сущностей ==================

    public Optional<Course> getCourse(Long courseId) {
        return courseRepository.findById(courseId);
    }

    public String getCourseTitle(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getTitle).orElse("");
    }

    public String getCourseDescription(Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    public String getSectionTitle(Long sectionId) {
        return sectionRepository.findById(sectionId).map(Section::getTitle).orElse("");
    }

    public String getSectionDescription(Long sectionId) {
        return sectionRepository.findById(sectionId).map(Section::getDescription).orElse("");
    }

    public String getTopicTitle(Long topicId) {
        return topicRepository.findById(topicId).map(Topic::getTitle).orElse("");
    }

    public Optional<Block> getBlockWithImages(Long blockId) {
        return blockRepository.findByIdWithImages(blockId);
    }

    public Optional<Block> getBlock(Long blockId) {
        return blockRepository.findById(blockId);
    }

    public List<Question> getQuestionsForBlock(Long blockId) {
        return questionRepository.findByBlockIdOrderByOrderIndexAsc(blockId);
    }

    public Optional<Question> getQuestion(Long questionId) {
        return questionRepository.findById(questionId);
    }

    @Transactional(readOnly = true)
    public Optional<Question> getQuestionWithImagesAndOptions(Long questionId) {
        return questionRepository.findById(questionId).map(q -> {
            q.getImages().size();         // инициализируем lazy-коллекции
            q.getAnswerOptions().size();
            return q;
        });
    }

    public List<AnswerOption> getAnswerOptionsForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);
    }

    public List<Block> getTopicBlocksWithQuestions(Long topicId) {
        return blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
    }

    public List<Question> getAllQuestionsForTopic(Long topicId) {
        return blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId).stream()
                .flatMap(block -> questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId()).stream())
                .toList();
    }

    // ================== Прогресс и статусы ==================

    /** Возвращает последний прогресс по каждому вопросу темы в заданном режиме. */
    private Map<Long, UserProgress> getLatestProgressForTopic(Long userId, Long topicId, String mode) {
        Map<Long, UserProgress> latest = new HashMap<>();
        for (UserProgress p : userProgressRepository.findByUserIdAndTopicIdAndMode(userId, topicId, mode)) {
            Long qid = p.getQuestion().getId();
            UserProgress existing = latest.get(qid);
            if (existing == null || p.getCompletedAt().isAfter(existing.getCompletedAt())) {
                latest.put(qid, p);
            }
        }
        return latest;
    }

    private String getTopicLearningStatus(Long userId, Long topicId) {
        List<Question> questions = getAllQuestionsForTopic(topicId);
        if (questions.isEmpty()) return EMOJI_NOT_STARTED;

        Map<Long, UserProgress> progressMap = getLatestProgressForTopic(userId, topicId, MODE_LEARNING);
        int answered = 0, correct = 0;
        for (Question q : questions) {
            UserProgress p = progressMap.get(q.getId());
            if (p != null) {
                answered++;
                if (p.getAnswerResult()) correct++;
            }
        }

        if (answered == 0) return EMOJI_NOT_STARTED;
        if (correct == questions.size()) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    public String getTopicStatusEmoji(Long userId, Long topicId) {
        return getTopicLearningStatus(userId, topicId);
    }

    public String getSectionStatusEmoji(Long userId, Long sectionId) {
        List<Topic> topics = topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);
        if (topics.isEmpty()) return EMOJI_NOT_STARTED;

        boolean anyLearning = false;
        boolean allTopicsGreen = true;

        for (Topic topic : topics) {
            if (questionRepository.countByTopicId(topic.getId()) == 0) continue;
            String status = getTopicLearningStatus(userId, topic.getId());
            if (!EMOJI_NOT_STARTED.equals(status)) anyLearning = true;
            if (!EMOJI_COMPLETED.equals(status)) allTopicsGreen = false;
        }

        String testStatus = getSectionTestStatus(userId, sectionId);
        if (!anyLearning && EMOJI_NOT_STARTED.equals(testStatus)) return EMOJI_NOT_STARTED;
        if (allTopicsGreen && EMOJI_COMPLETED.equals(testStatus)) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    public String getCourseStatusEmoji(Long userId, Long courseId) {
        List<Section> sections = sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
        if (sections.isEmpty()) return EMOJI_NOT_STARTED;

        boolean anyLearning = false;
        boolean allSectionsGreen = true;

        for (Section section : sections) {
            boolean hasQuestions = topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId())
                    .stream().anyMatch(t -> questionRepository.countByTopicId(t.getId()) > 0);
            if (!hasQuestions) continue;

            String status = getSectionStatusEmoji(userId, section.getId());
            if (!EMOJI_NOT_STARTED.equals(status)) anyLearning = true;
            if (!EMOJI_COMPLETED.equals(status)) allSectionsGreen = false;
        }

        String courseTestStatus = getCourseTestStatus(userId, courseId);
        if (!anyLearning && EMOJI_NOT_STARTED.equals(courseTestStatus)) return EMOJI_NOT_STARTED;
        if (allSectionsGreen && EMOJI_COMPLETED.equals(courseTestStatus)) return EMOJI_COMPLETED;
        return EMOJI_IN_PROGRESS;
    }

    private String computeTestStatusEmoji(Optional<UserTestResult> resultOpt) {
        return resultOpt.map(r -> {
            int total = r.getCorrectCount() + r.getWrongCount();
            if (total == 0) return EMOJI_NOT_STARTED;
            double percent = (double) r.getCorrectCount() / total;
            if (percent >= 1.0) return EMOJI_COMPLETED;
            if (percent >= 0.5) return EMOJI_IN_PROGRESS;
            return EMOJI_FAILED;
        }).orElse(EMOJI_NOT_STARTED);
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

    // ================== Статистика пользователя ==================

    public long getTotalStartedCourses(Long userId) {
        return userProgressRepository.countDistinctCoursesByUserId(userId);
    }

    public long getCompletedCoursesCount(Long userId) {
        return userProgressRepository.findDistinctCourseIdsWithProgressByUserId(userId).stream()
                .filter(courseId -> {
                    long total = courseRepository.countQuestionsByCourseId(courseId);
                    long answered = userProgressRepository.countDistinctAnsweredQuestionsByUserAndCourse(userId, courseId);
                    return total > 0 && answered >= total;
                })
                .count();
    }

    public String getHardestCourse(Long userId) {
        List<Object[]> stats = userProgressRepository.getQuestionStatsByUserWithTitle(userId);
        if (stats.isEmpty()) return MSG_NO_DATA;

        Object[] hardest = null;
        double maxErrorRate = -1.0;
        for (Object[] row : stats) {
            long totalAnswers = (Long) row[2];
            long wrongAnswers = (Long) row[3];
            double errorRate = (double) wrongAnswers / totalAnswers;
            if (errorRate > maxErrorRate) {
                maxErrorRate = errorRate;
                hardest = row;
            }
        }

        if (hardest == null) return MSG_NO_DATA;
        return String.format("%s (%d%% ошибок)", hardest[1], (int) Math.round(maxErrorRate * 100));
    }

    public List<String> getCoursesProgress(Long userId) {
        List<Course> courses = courseRepository.findAll();
        if (courses.isEmpty()) return Collections.emptyList();

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        Map<Long, Long> totalMap = courseRepository.countQuestionsByCourseIds(courseIds);
        Map<Long, Long> answeredMap = userProgressRepository
                .countDistinctAnsweredQuestionsByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));

        List<String> result = new ArrayList<>();
        for (Course course : courses) {
            long total = totalMap.getOrDefault(course.getId(), 0L);
            if (total == 0) continue;
            long answered = answeredMap.getOrDefault(course.getId(), 0L);
            int percent = (int) Math.round(answered * 100.0 / total);
            String emoji = answered == 0 ? EMOJI_NOT_STARTED
                    : (answered < total ? EMOJI_IN_PROGRESS : EMOJI_COMPLETED);
            result.add(String.format(FORMAT_COURSE_PROGRESS, emoji, course.getTitle(), percent, answered, total));
        }
        return result;
    }

    // ================== Пагинация ==================

    public PaginationResult<Course> getMyCoursesPage(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Course> p = userProgressRepository.findCoursesWithProgressOrderByLastAccessed(userId, pageable);
        return PaginationResult.of(p);
    }

    public PaginationResult<Course> getAllCoursesPage(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("title").ascending());
        Page<Course> p = courseRepository.findAll(pageable);
        return PaginationResult.of(p);
    }

    public PaginationResult<Course> getFoundCoursesPage(String query, int page, int pageSize) {
        String fullTextQuery = prepareFullTextQuery(query);
        if (fullTextQuery.isEmpty()) {
            return PaginationResult.empty(page);
        }
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Course> p = courseRepository.searchByFullText(fullTextQuery, pageable);
        return PaginationResult.of(p);
    }

    public PaginationResult<Section> getSectionsPage(Long courseId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("orderIndex").ascending());
        Page<Section> p = sectionRepository.findByCourseId(courseId, pageable);
        return PaginationResult.of(p);
    }

    public PaginationResult<Topic> getTopicsPage(Long sectionId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("orderIndex").ascending());
        Page<Topic> p = topicRepository.findBySectionId(sectionId, pageable);
        return PaginationResult.of(p);
    }

    // ================== Последние посещения ==================

    public Instant getCourseLastAccessed(Long userId, Long courseId) {
        return userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId)
                .stream().findFirst()
                .map(UserProgress::getLastAccessedAt)
                .orElse(null);
    }

    /** Обновляет время последнего посещения курса (создаёт запись при необходимости). */
    @Transactional
    public void updateCourseLastAccessed(Long userId, Long courseId) {
        if (courseId == null || !courseRepository.existsById(courseId)) return;
        upsertCourseLastAccessed(userId, courseId);
    }

    /** Псевдоним для {@link #updateCourseLastAccessed} — оставлен для совместимости с уже зафиксированными вызовами. */
    @Transactional
    public void updateCourseLastAccessedOnExit(Long userId, Long courseId) {
        if (courseId == null) return;
        if (!courseRepository.existsById(courseId)) {
            log.warn("Course {} does not exist, skipping last accessed update", courseId);
            return;
        }
        upsertCourseLastAccessed(userId, courseId);
    }

    private void upsertCourseLastAccessed(Long userId, Long courseId) {
        List<UserProgress> list = userProgressRepository
                .findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, courseId);
        UserProgress progress = list.isEmpty() ? buildCourseProgress(userId, courseId) : list.get(0);
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    private UserProgress buildCourseProgress(Long userId, Long courseId) {
        UserProgress p = new UserProgress();
        p.setUserId(userId);
        p.setCourse(courseRepository.getReferenceById(courseId));
        p.setIsPassed(false);
        p.setMode(MODE_LEARNING);
        return p;
    }

    @Transactional
    public void updateSectionLastAccessed(Long userId, Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new RuntimeException("Section not found: " + sectionId));
        UserProgress progress = userProgressRepository
                .findByUserIdAndSection(userId, section)
                .orElseGet(() -> {
                    UserProgress p = new UserProgress();
                    p.setUserId(userId);
                    p.setSection(section);
                    p.setCourse(section.getCourse());
                    p.setIsPassed(false);
                    p.setMode(MODE_LEARNING);
                    return p;
                });
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    public Instant getSectionLastAccessed(Long userId, Long sectionId) {
        return userProgressRepository
                .findByUserIdAndSectionIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(userId, sectionId)
                .stream().findFirst()
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
                    UserProgress p = new UserProgress();
                    p.setUserId(userId);
                    p.setSection(section);
                    p.setCourse(section.getCourse());
                    p.setIsPassed(false);
                    p.setMode(MODE_LEARNING);
                    return p;
                });
        progress.setLastAccessedAt(Instant.now());
        userProgressRepository.save(progress);
    }

    // ================== Прогресс ответов и ошибки ==================

    @Transactional
    public void saveAnswerProgress(Long userId, Long questionId, boolean correct, boolean isLearning) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
        UserProgress progress = new UserProgress();
        progress.setUserId(userId);
        progress.setCourse(question.getBlock().getTopic().getSection().getCourse());
        progress.setQuestion(question);
        progress.setIsPassed(true);
        progress.setAnswerResult(correct);
        progress.setCompletedAt(Instant.now());
        progress.setMode(isLearning ? MODE_LEARNING : MODE_TEST);
        userProgressRepository.save(progress);
        upsertCourseLastAccessed(userId, progress.getCourse().getId());
    }

    @Transactional
    public void recordMistake(Long userId, Long questionId) {
        UserMistake mistake = userMistakeRepository.findByUserIdAndQuestionId(userId, questionId)
                .orElse(new UserMistake());
        mistake.setUserId(userId);
        mistake.setQuestion(questionRepository.getReferenceById(questionId));
        mistake.setLastMistakeAt(Instant.now());
        userMistakeRepository.save(mistake);
    }

    @Transactional
    public void clearMistake(Long userId, Long questionId) {
        userMistakeRepository.deleteByUserIdAndQuestionId(userId, questionId);
    }

    public List<Question> getMistakeQuestions(Long userId) {
        return userMistakeRepository.findMistakeQuestionsByUserId(userId);
    }

    // ================== Тесты и случайные вопросы ==================

    public List<Question> getRandomQuestionsForSection(Long sectionId, int questionsPerBlock) {
        List<Question> result = new ArrayList<>();
        for (Topic topic : topicRepository.findBySectionIdOrderByOrderIndexAsc(sectionId)) {
            for (Block block : blockRepository.findByTopicIdOrderByOrderIndexAsc(topic.getId())) {
                List<Question> qs = new ArrayList<>(questionRepository.findByBlockIdOrderByOrderIndexAsc(block.getId()));
                if (!qs.isEmpty()) {
                    Collections.shuffle(qs);
                    result.addAll(qs.stream().limit(questionsPerBlock).toList());
                }
            }
        }
        return result;
    }

    public List<Question> getRandomQuestionsForCourse(Long courseId, int questionsPerTopic) {
        List<Question> result = new ArrayList<>();
        for (Section section : sectionRepository.findByCourseIdOrderByOrderIndexAsc(courseId)) {
            for (Topic topic : topicRepository.findBySectionIdOrderByOrderIndexAsc(section.getId())) {
                List<Question> qs = new ArrayList<>(getAllQuestionsForTopic(topic.getId()));
                if (!qs.isEmpty()) {
                    Collections.shuffle(qs);
                    result.addAll(qs.stream().limit(questionsPerTopic).toList());
                }
            }
        }
        return result;
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

    // ================== Статусы для списков (batch) ==================

    public Map<Long, String> getCourseStatusesForUser(Long userId, List<Long> courseIds) {
        if (courseIds.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> totalMap = courseRepository.countQuestionsByCourseIds(courseIds);
        Map<Long, Long> answeredMap = userProgressRepository
                .countDistinctAnsweredQuestionsByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
        Map<Long, Long> correctMap = userProgressRepository
                .countCorrectLearningAnswersByUserAndCourses(userId, courseIds)
                .stream().collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
        Map<Long, String> testStatusMap = getCourseTestStatusesForUser(userId, courseIds);

        Map<Long, String> result = new HashMap<>();
        for (Long courseId : courseIds) {
            long total = totalMap.getOrDefault(courseId, 0L);
            if (total == 0) {
                result.put(courseId, EMOJI_NOT_STARTED);
                continue;
            }
            long answered = answeredMap.getOrDefault(courseId, 0L);
            long correct = correctMap.getOrDefault(courseId, 0L);
            String testStatus = testStatusMap.getOrDefault(courseId, EMOJI_NOT_STARTED);

            if (answered == 0 && EMOJI_NOT_STARTED.equals(testStatus)) {
                result.put(courseId, EMOJI_NOT_STARTED);
            } else if (answered == total && correct == total && EMOJI_COMPLETED.equals(testStatus)) {
                result.put(courseId, EMOJI_COMPLETED);
            } else {
                result.put(courseId, EMOJI_IN_PROGRESS);
            }
        }
        return result;
    }

    public Map<Long, String> getCourseTestStatusesForUser(Long userId, List<Long> courseIds) {
        if (courseIds.isEmpty()) return Collections.emptyMap();

        Map<Long, String> map = new HashMap<>();
        for (Object[] row : userTestResultRepository.findTestResultsByUserAndTestIds(userId, TEST_TYPE_COURSE, courseIds)) {
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

    // ================== Дополнительные методы совместимости ==================

    public Optional<Block> getNextBlock(Long currentBlockId, Long topicId) {
        Block current = blockRepository.findById(currentBlockId).orElse(null);
        if (current == null) return Optional.empty();
        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
        int idx = blocks.indexOf(current);
        return (idx >= 0 && idx < blocks.size() - 1) ? Optional.of(blocks.get(idx + 1)) : Optional.empty();
    }

    public Optional<Block> getPrevBlock(Long currentBlockId, Long topicId) {
        Block current = blockRepository.findById(currentBlockId).orElse(null);
        if (current == null) return Optional.empty();
        List<Block> blocks = blockRepository.findByTopicIdOrderByOrderIndexAsc(topicId);
        int idx = blocks.indexOf(current);
        return (idx > 0) ? Optional.of(blocks.get(idx - 1)) : Optional.empty();
    }

    public Optional<Section> getSection(Long sectionId) {
        return sectionRepository.findById(sectionId);
    }

    public Optional<Topic> getTopic(Long topicId) {
        return topicRepository.findById(topicId);
    }

    // ================== Методы для генерации PDF ==================

    @Transactional(readOnly = true)
    public Optional<Course> getCourseWithContent(Long courseId) {
        Optional<Course> courseOpt = courseRepository.findByIdWithSections(courseId);
        if (courseOpt.isEmpty()) return Optional.empty();

        Course course = courseOpt.get();
        List<Long> topicIds = course.getSections().stream()
                .flatMap(s -> s.getTopics().stream())
                .map(Topic::getId)
                .toList();

        if (!topicIds.isEmpty()) {
            Map<Long, Topic> topicMap = topicRepository.findByIdsWithBlocks(topicIds).stream()
                    .collect(Collectors.toMap(Topic::getId, t -> t));

            for (Section section : course.getSections()) {
                List<Topic> hydrated = section.getTopics().stream()
                        .map(t -> topicMap.getOrDefault(t.getId(), t))
                        .toList();
                section.getTopics().clear();
                section.getTopics().addAll(hydrated);
            }

            List<Long> blockIds = topicMap.values().stream()
                    .flatMap(t -> t.getBlocks().stream())
                    .map(Block::getId)
                    .toList();
            hydrateBlockQuestions(topicMap.values(), blockIds);
        }
        return Optional.of(course);
    }

    @Transactional(readOnly = true)
    public Optional<Section> getSectionWithContent(Long sectionId) {
        Optional<Section> sectionOpt = sectionRepository.findByIdWithTopics(sectionId);
        if (sectionOpt.isEmpty()) return Optional.empty();

        Section section = sectionOpt.get();
        List<Long> topicIds = section.getTopics().stream().map(Topic::getId).toList();
        if (topicIds.isEmpty()) return Optional.of(section);

        Map<Long, Topic> topicMap = topicRepository.findByIdsWithBlocks(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, t -> t));
        List<Topic> hydrated = section.getTopics().stream()
                .map(t -> topicMap.getOrDefault(t.getId(), t)).toList();
        section.getTopics().clear();
        section.getTopics().addAll(hydrated);

        List<Long> blockIds = topicMap.values().stream()
                .flatMap(t -> t.getBlocks().stream()).map(Block::getId).toList();
        hydrateBlockQuestions(topicMap.values(), blockIds);
        return Optional.of(section);
    }

    @Transactional(readOnly = true)
    public Optional<Topic> getTopicWithBlocks(Long topicId) {
        Optional<Topic> topicOpt = topicRepository.findByIdWithBlocks(topicId);
        if (topicOpt.isEmpty()) return Optional.empty();

        Topic topic = topicOpt.get();
        List<Long> blockIds = topic.getBlocks().stream().map(Block::getId).toList();
        hydrateBlockQuestions(List.of(topic), blockIds);
        return Optional.of(topic);
    }

    /** Загружает вопросы с вариантами для указанных блоков и присваивает их. */
    private void hydrateBlockQuestions(Iterable<Topic> topics, List<Long> blockIds) {
        if (blockIds.isEmpty()) return;
        Map<Long, List<Question>> questionsByBlock = questionRepository.findAllByBlockIdWithOptions(blockIds)
                .stream().collect(Collectors.groupingBy(q -> q.getBlock().getId()));

        for (Topic topic : topics) {
            for (Block block : topic.getBlocks()) {
                List<Question> qs = questionsByBlock.getOrDefault(block.getId(), List.of());
                block.getQuestions().clear();
                block.getQuestions().addAll(qs);
            }
        }
    }

    // ================== Вспомогательные методы ==================

    private String prepareFullTextQuery(String userInput) {
        if (userInput == null || userInput.isBlank()) return "";
        String cleaned = userInput.replaceAll("[^\\p{L}\\p{N}\\s]+", "").trim();
        if (cleaned.isEmpty()) return "";
        return String.join(" & ", cleaned.split("\\s+"));
    }

    private String formatRelativeTime(Instant instant) {
        if (instant == null) return "";
        long seconds = Duration.between(instant, Instant.now()).getSeconds();
        if (seconds < 0) return "";
        if (seconds < 60) return TIME_JUST_NOW;
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + TIME_MINUTES_AGO;
        long hours = minutes / 60;
        if (hours < 24) return hours + TIME_HOURS_AGO;
        long days = hours / 24;
        if (days < 7) return days + TIME_DAYS_AGO;
        return DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(instant);
    }

    // Unused — kept for future use
    public String getLastAccessedTime(Long userId, Long courseId) {
        return getCourseLastAccessed(userId, courseId) != null
                ? formatRelativeTime(getCourseLastAccessed(userId, courseId)) : "";
    }
}
