package com.backend.backend.repository;

import com.backend.backend.entity.Analysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /**
     * {@code pattern} is a lowercase LIKE pattern ("%" for all). {@code exportedOnly}/{@code draftsOnly}
     * filter by export status; both false means no status filter.
     */
    @EntityGraph(attributePaths = "cv")
    @Query("""
            select a from Analysis a
            where (lower(a.jobTitle) like :pattern or lower(a.cv.fileName) like :pattern)
              and (:exportedOnly = false or a.exported = true)
              and (:draftsOnly = false or a.exported = false)
            """)
    Page<Analysis> search(@Param("pattern") String pattern,
                          @Param("exportedOnly") boolean exportedOnly,
                          @Param("draftsOnly") boolean draftsOnly,
                          Pageable pageable);

    @EntityGraph(attributePaths = "cv")
    List<Analysis> findTop5ByOrderByCreatedAtDesc();

    long countByExportedTrue();

    @Query("select avg(a.score) from Analysis a")
    Double averageScore();

    @Query("select max(a.score) from Analysis a")
    Integer bestScore();
}
