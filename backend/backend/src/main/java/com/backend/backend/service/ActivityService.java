package com.backend.backend.service;

import com.backend.backend.entity.Activity;
import com.backend.backend.model.ActivityType;
import com.backend.backend.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final ActivityRepository repository;

    public ActivityService(ActivityRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction so the feed only shows actions that actually committed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(ActivityType type, String label) {
        repository.save(new Activity(type, label));
    }
}
