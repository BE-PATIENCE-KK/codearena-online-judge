package com.codearena.codearena.service.imp1;

import com.codearena.codearena.dto.JudgeResult;
import com.codearena.codearena.entity.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void judgesAcceptedAndWrongAnswer() {
        JudgeServiceImpl judge = judgeService();

        assertEquals("AC", judge.judge("Java", program("System.out.println(2 + 3);"), problem()).getStatus());
        assertEquals("WA", judge.judge("Java", program("System.out.println(2 + 4);"), problem()).getStatus());
    }

    @Test
    void reportsCompileError() {
        JudgeResult result = judgeService().judge("Java", program("this is not valid Java"), problem());

        assertEquals("CE", result.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getMessage());
    }

    @Test
    void killsTimedOutProgram() {
        JudgeResult result = judgeService().judge("Java",
                program("while (true) { }"), problem());

        assertEquals("TLE", result.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getTimeUsed() >= 1000);
    }

    @Test
    void reportsRuntimeError() {
        JudgeResult result = judgeService().judge("Java",
                program("throw new RuntimeException(\"boom\");"), problem());

        assertEquals("RE", result.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getMessage());
    }

    @Test
    void capsWrongAnswerDetailsToDatabaseColumnSize() {
        String noisyOutput = "System.out.println(\"x\".repeat(70000));";
        JudgeResult result = judgeService().judge("Java", program(noisyOutput), problem());

        assertEquals("WA", result.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getMessage().length() <= 1000);
    }

    @Test
    void removesTemporaryWorkspaceAfterJudging() throws Exception {
        JudgeServiceImpl judge = judgeService();
        judge.judge("Java", program("System.out.println(2 + 3);"), problem());

        try (var children = java.nio.file.Files.list(tempDir)) {
            assertEquals(0, children.count());
        }
    }

    private JudgeServiceImpl judgeService() {
        return new JudgeServiceImpl(tempDir.toString(), 2000);
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setSampleInput("2 3\n");
        problem.setSampleOutput("5\n");
        problem.setTimeLimit(1000);
        return problem;
    }

    private String program(String statement) {
        return "public class Main { public static void main(String[] args) { " + statement + " } }";
    }
}
