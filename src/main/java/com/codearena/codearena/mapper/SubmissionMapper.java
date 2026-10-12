package com.codearena.codearena.mapper;

import com.codearena.codearena.entity.Submission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SubmissionMapper {

    @Insert("""
            INSERT INTO submission (user_id, problem_id, answer, language, status)
            VALUES (#{userId}, #{problemId}, #{answer}, #{language}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Submission submission);

    @Update("UPDATE submission SET status = #{status} WHERE id = #{id}")
    int updateStatus(Submission submission);

    @Update("""
            UPDATE submission
               SET status = #{status},
                   time_used = #{timeUsed},
                   error_message = #{errorMessage}
             WHERE id = #{id}
            """)
    int updateJudgeResult(Submission submission);

    @Select("""
            SELECT s.id, s.user_id, s.problem_id, s.answer, s.language, s.status,
                   s.time_used, s.error_message,
                   s.created_at, s.updated_at,
                   p.title AS problem_title, p.difficulty AS problem_difficulty
            FROM submission s
            INNER JOIN problem p ON p.id = s.problem_id
            WHERE s.user_id = #{userId}
            ORDER BY s.created_at DESC, s.id DESC
            """)
    List<Submission> findByUserId(Long userId);
    @Select("""
        SELECT s.id, s.user_id, s.problem_id, s.answer, s.language, s.status,
               s.time_used, s.error_message,
               s.created_at, s.updated_at,
               p.title AS problem_title, p.difficulty AS problem_difficulty
        FROM submission s
        INNER JOIN problem p ON p.id = s.problem_id
        WHERE s.id = #{submissionId}
          AND s.user_id = #{userId}
        """)
    Submission findByIdAndUserId(Long submissionId, Long userId);
}
