package com.codearena.codearena.service.imp1;

import com.codearena.codearena.dto.CreateSubmissionRequest;
import com.codearena.codearena.dto.JudgeResult;
import com.codearena.codearena.entity.Problem;
import com.codearena.codearena.entity.Submission;
import com.codearena.codearena.entity.SubmissionStatus;
import com.codearena.codearena.entity.User;
import com.codearena.codearena.exception.ResourceNotFoundException;
import com.codearena.codearena.mapper.ProblemMapper;
import com.codearena.codearena.mapper.SubmissionMapper;
import com.codearena.codearena.mapper.UserMapper;
import com.codearena.codearena.service.JudgeService;
import com.codearena.codearena.service.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubmissionServiceImpl implements SubmissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionServiceImpl.class);
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final JudgeService judgeService;

    public SubmissionServiceImpl(SubmissionMapper submissionMapper,
                                 UserMapper userMapper,
                                 ProblemMapper problemMapper,
                                 JudgeService judgeService) {
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemMapper = problemMapper;
        this.judgeService = judgeService;
    }

    /**
     * 提交代码。
     *
     * <p>Day 8 起这里不再只是“记录提交”，而是：先把提交落库拿到 id，
     * 再调用 JudgeService 编译运行，最后把判题结果回写到同一条记录。</p>
     *
     * <p>注意：这个方法故意不加 {@code @Transactional}。判题里的编译 + 运行最多要几秒钟，
     * 如果放在事务里会长时间占用数据库连接；而且判题失败不应该把已经保存的提交记录回滚掉。</p>
     */
    @Override
    public Submission submit(String username, CreateSubmissionRequest request) {
        if (request.getProblemId() <= 0) {
            throw new IllegalArgumentException("题目编号不合法");
        }

        User user = findUser(username);
        Problem problem = problemMapper.findById(request.getProblemId());
        if (problem == null) {
            throw new ResourceNotFoundException("题目不存在");
        }

        Submission submission = new Submission();
        submission.setUserId(user.getId());
        submission.setProblemId(problem.getId());
        submission.setAnswer(request.getAnswer().trim());
        submission.setLanguage(request.getLanguage().trim());
        submission.setStatus(SubmissionStatus.PENDING.getCode());

        int affectedRows = submissionMapper.insert(submission);
        if (affectedRows != 1) {
            throw new IllegalStateException("提交保存失败");
        }

        transition(submission, SubmissionStatus.RUNNING);
        if (submissionMapper.updateStatus(submission) != 1) {
            throw new IllegalStateException("提交状态更新失败");
        }

        JudgeResult judgeResult;
        try {
            judgeResult = judgeService.judge(
                    submission.getLanguage(),
                    submission.getAnswer(),
                    problem
            );
        } catch (RuntimeException exception) {
            // The initial submission row is already committed. Convert unexpected judge failures
            // to the existing SE state so an ordinary judge exception does not leave SUBMITTED forever.
            LOGGER.error("判题调用失败，提交编号 {}", submission.getId(), exception);
            judgeResult = JudgeResult.error(JudgeResult.SE, "判题服务暂时异常，请稍后重试");
        }
        SubmissionStatus finalStatus = SubmissionStatus.fromCode(judgeResult.getStatus());
        if (finalStatus == null) {
            finalStatus = SubmissionStatus.SYSTEM_ERROR;
        }
        transition(submission, finalStatus);
        submission.setTimeUsed(judgeResult.getTimeUsed());
        submission.setErrorMessage(judgeResult.getMessage());
        if (submissionMapper.updateJudgeResult(submission) != 1) {
            throw new IllegalStateException("判题结果保存失败");
        }

        submission.setProblemTitle(problem.getTitle());
        submission.setProblemDifficulty(problem.getDifficulty());
        return submission;
    }

    private void transition(Submission submission, SubmissionStatus nextStatus) {
        SubmissionStatus currentStatus = SubmissionStatus.fromCode(submission.getStatus());
        if (currentStatus == null || !currentStatus.canTransitionTo(nextStatus)) {
            throw new IllegalStateException("非法的判题状态转换");
        }
        submission.setStatus(nextStatus.getCode());
    }

    @Override
    public List<Submission> findMine(String username) {
        return submissionMapper.findByUserId(findUser(username).getId());
    }
    @Override
    public Submission findById(String username, Long submissionId) {
        if (submissionId == null || submissionId <= 0) {
            throw new IllegalArgumentException("提交编号不合法");
        }

        User user = findUser(username);

        Submission submission =
                submissionMapper.findByIdAndUserId(
                        submissionId,
                        user.getId()
                );

        if (submission == null) {
            throw new ResourceNotFoundException("提交记录不存在");
        }

        return submission;
    }

    private User findUser(String username) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("当前用户不存在");
        }
        return user;
    }
}
