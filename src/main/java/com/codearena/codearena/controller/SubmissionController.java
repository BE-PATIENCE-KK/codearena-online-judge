package com.codearena.codearena.controller;
import org.springframework.web.bind.annotation.PathVariable;

import com.codearena.codearena.dto.CreateSubmissionRequest;
import com.codearena.codearena.entity.Result;
import com.codearena.codearena.entity.Submission;
import com.codearena.codearena.service.SubmissionService;
import com.codearena.codearena.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/submission")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<Result<Submission>> submit(@Valid @RequestBody CreateSubmissionRequest request,
                                         HttpServletRequest httpRequest) {
        Submission submission = submissionService.submit(getUsername(httpRequest), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(submission));
    }

    @GetMapping("/mine")
    public Result<List<Submission>> findMine(HttpServletRequest httpRequest) {
        List<Submission> submissions = submissionService.findMine(getUsername(httpRequest));
        return Result.success(submissions);
    }
    @GetMapping("/{id}")
    public Result<Submission> findById(@PathVariable Long id,
                           HttpServletRequest httpRequest) {
        Submission submission =
                submissionService.findById(
                        getUsername(httpRequest),
                        id
                );

        return Result.success(submission);
    }

    private String getUsername(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return JwtUtil.getUsername(authorization.substring(7));
    }
}
