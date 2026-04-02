package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    // Можно добавить методы поиска по названию, если нужно
    Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    @Query("SELECT c.id, COUNT(q) FROM Course c JOIN c.sections s JOIN s.topics t JOIN t.blocks b JOIN b.questions q WHERE c.id IN :courseIds GROUP BY c.id")
    List<Object[]> countQuestionsByCourseIdsRaw(@Param("courseIds") List<Long> courseIds);

    default Map<Long, Long> countQuestionsByCourseIds(List<Long> courseIds) {
        return countQuestionsByCourseIdsRaw(courseIds).stream()
                .collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
    }
    // Общее количество вопросов в курсе
    @Query("SELECT COUNT(q) FROM Course c JOIN c.sections s JOIN s.topics t JOIN t.blocks b JOIN b.questions q WHERE c.id = :courseId")
    long countQuestionsByCourseId(@Param("courseId") Long courseId);

    @Query(value = """
            SELECT * FROM course 
            WHERE to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(description, '')) 
                  @@ to_tsquery('russian', :query)
            ORDER BY ts_rank(
                to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(description, '')),
                to_tsquery('russian', :query)
            ) DESC
            """,
            countQuery = """
                    SELECT count(*) FROM course 
                    WHERE to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(description, '')) 
                          @@ to_tsquery('russian', :query)
                    """,
            nativeQuery = true)
    Page<Course> searchByFullText(@Param("query") String query, Pageable pageable);
}