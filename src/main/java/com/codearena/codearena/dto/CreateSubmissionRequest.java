package com.codearena.codearena.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateSubmissionRequest {

    @NotNull(message = "题目编号不能为空")
    private Long problemId;

    @NotBlank(message = "提交内容不能为空")
    @Size(max = 16000, message = "提交内容不能超过 16000 个字符")
    private String answer;

    @NotBlank(message = "编程语言不能为空")
    @Size(max = 30, message = "编程语言不能超过 30 个字符")
    private String language;

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
