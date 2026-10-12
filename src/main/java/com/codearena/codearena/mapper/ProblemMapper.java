package com.codearena.codearena.mapper;

import com.codearena.codearena.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProblemMapper {

    @Select("SELECT id, title, description, difficulty, input_description, output_description, sample_input, sample_output, time_limit, memory_limit, created_at FROM problem ORDER BY id")
    List<Problem> findAll();

    @Select("SELECT id, title, description, difficulty, input_description, output_description, sample_input, sample_output, time_limit, memory_limit, created_at FROM problem WHERE id = #{id}")
    Problem findById(Long id);
}
