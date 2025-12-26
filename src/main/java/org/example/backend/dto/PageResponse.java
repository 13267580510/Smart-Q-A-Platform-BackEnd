package org.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> implements Serializable {
    private List<T> data;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    public PageResponse(){};
    public  static <T> PageResponse<T> fromPage(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber() + 1, // 前端通常从1开始
                page.getSize()
        );
    }
}