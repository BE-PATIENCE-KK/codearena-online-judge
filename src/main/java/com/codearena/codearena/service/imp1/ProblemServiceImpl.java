package com.codearena.codearena.service.imp1;

import com.codearena.codearena.entity.Problem;
import com.codearena.codearena.mapper.ProblemMapper;
import com.codearena.codearena.service.ProblemService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    public ProblemServiceImpl(ProblemMapper problemMapper) {
        this.problemMapper = problemMapper;
    }

    @Override
    public List<Problem> findAll() {
        return problemMapper.findAll();
    }

    @Override
    public Problem findById(Long id) {
        return problemMapper.findById(id);
    }
}