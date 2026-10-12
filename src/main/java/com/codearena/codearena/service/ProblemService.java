package com.codearena.codearena.service;

import com.codearena.codearena.entity.Problem;

import java.util.List;

public interface ProblemService {

    List<Problem> findAll();

    Problem findById(Long id);
}