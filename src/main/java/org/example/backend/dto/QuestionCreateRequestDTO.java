package org.example.backend.dto;

import java.io.Serializable;

public record QuestionCreateRequestDTO(
        String title,
        String content,
        String username,
        String  categoryId
) implements Serializable {}