package com.backend.backend.repository;

import com.backend.backend.entity.Cv;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CvRepository extends JpaRepository<Cv, UUID> {

    /** {@code pattern} is a lowercase LIKE pattern; pass "%" to match everything. */
    @Query("select c from Cv c where lower(c.fileName) like :pattern or lower(c.language) like :pattern")
    Page<Cv> search(@Param("pattern") String pattern, Pageable pageable);
}
