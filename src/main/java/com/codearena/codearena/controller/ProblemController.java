package com.codearena.codearena.controller;

import com.codearena.codearena.entity.Problem;
import com.codearena.codearena.entity.Result;
import com.codearena.codearena.service.ProblemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/problem")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping("/list")
    public Result<List<Problem>> list() {
        List<Problem> problems = problemService.findAll();
        return Result.success(problems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<Problem>> getById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(Result.error(400, "题目编号不合法"));
        }

        Problem problem = problemService.findById(id);
        if (problem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "题目不存在"));
        }

        return ResponseEntity.ok(Result.success(problem));
    }
}
