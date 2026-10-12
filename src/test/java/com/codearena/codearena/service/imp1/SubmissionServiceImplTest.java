package com.codearena.codearena.service.imp1;

import com.codearena.codearena.dto.CreateSubmissionRequest;
import com.codearena.codearena.entity.Problem;
import com.codearena.codearena.entity.Submission;
import com.codearena.codearena.entity.User;
import com.codearena.codearena.mapper.ProblemMapper;
import com.codearena.codearena.mapper.SubmissionMapper;
import com.codearena.codearena.mapper.UserMapper;
import com.codearena.codearena.service.JudgeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceImplTest {

    @Test
    void persistsSystemErrorWhenJudgeThrows() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        JudgeService judgeService = mock(JudgeService.class);
        User user = new User();
        user.setId(7L);
        Problem problem = new Problem();
        problem.setId(9L);
        problem.setTitle("test");
        CreateSubmissionRequest request = new CreateSubmissionRequest();
        request.setProblemId(9L);
        request.setAnswer("public class Main {}");
        request.setLanguage("Java");

        when(userMapper.findByUsername("candidate")).thenReturn(user);
        when(problemMapper.findById(9L)).thenReturn(problem);
        when(submissionMapper.insert(any(Submission.class))).thenAnswer(invocation -> {
            ((Submission) invocation.getArgument(0)).setId(11L);
            return 1;
        });
        when(submissionMapper.updateStatus(any(Submission.class))).thenReturn(1);
        when(judgeService.judge("Java", request.getAnswer(), problem))
                .thenThrow(new IllegalStateException("private internal detail"));
        when(submissionMapper.updateJudgeResult(any(Submission.class))).thenReturn(1);

        Submission result = new SubmissionServiceImpl(
                submissionMapper, userMapper, problemMapper, judgeService
        ).submit("candidate", request);

        assertEquals("SE", result.getStatus());
        assertEquals("判题服务暂时异常，请稍后重试", result.getErrorMessage());
        verify(submissionMapper).updateJudgeResult(result);
    }
}
