package com.backend.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The original upload (never modified) and its plain-text extraction, keyed by the CV id. */
@Entity
@Table(name = "cv_file")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CvFile {

    @Id
    @Column(name = "cv_id")
    private UUID cvId;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;
}
