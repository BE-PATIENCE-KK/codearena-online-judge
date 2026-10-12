package com.codearena.codearena.service;

import com.codearena.codearena.dto.CreateSubmissionRequest;
import com.codearena.codearena.entity.Submission;

import java.util.List;

public interface SubmissionService {

    Submission submit(String username, CreateSubmissionRequest request);

    List<Submission> findMine(String username);

    Submission findById(String username, Long submissionId);
}
