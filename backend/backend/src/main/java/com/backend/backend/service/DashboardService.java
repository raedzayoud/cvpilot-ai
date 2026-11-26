package com.backend.backend.service;

import com.backend.backend.dto.DashboardDto;
import com.backend.backend.mapper.AnalysisMapper;
import com.backend.backend.mapper.JobMapper;
import com.backend.backend.repository.ActivityRepository;
import com.backend.backend.repository.AnalysisRepository;
import com.backend.backend.repository.CvRepository;
import com.backend.backend.repository.JobPostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every number on the dashboard is a live query; nothing is cached or estimated. */
@Service
public class DashboardService {

    private final CvRepository cvs;
    private final JobPostingRepository jobs;
    private final AnalysisRepository analyses;
    private final ActivityRepository activity;
    private final JobMapper jobMapper;
    private final AnalysisMapper analysisMapper;

    public DashboardService(CvRepository cvs, JobPostingRepository jobs, AnalysisRepository analyses, ActivityRepository activity,
                            JobMapper jobMapper, AnalysisMapper analysisMapper) {
        this.cvs = cvs;
        this.jobs = jobs;
        this.analyses = analyses;
        this.activity = activity;
        this.jobMapper = jobMapper;
        this.analysisMapper = analysisMapper;
    }

    @Transactional(readOnly = true)
    public DashboardDto.Response get() {
        Double average = analyses.averageScore();
        DashboardDto.Stats stats = new DashboardDto.Stats(
                cvs.count(),
                analyses.count(),
                analyses.countByExportedTrue(),
                average == null ? null : (int) Math.round(average),
                analyses.bestScore());
        return new DashboardDto.Response(
                stats,
                jobs.findTop3ByOrderByCreatedAtDesc().stream().map(jobMapper::toResponse).toList(),
                analyses.findTop5ByOrderByCreatedAtDesc().stream().map(analysisMapper::toSummary).toList(),
                activity.findTop8ByOrderByIdDesc().stream().map(analysisMapper::toResponse).toList());
    }
}
