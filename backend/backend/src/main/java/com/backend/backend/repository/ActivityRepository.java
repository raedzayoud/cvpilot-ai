package com.backend.backend.repository;

import com.backend.backend.entity.Activity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findTop8ByOrderByIdDesc();
}
