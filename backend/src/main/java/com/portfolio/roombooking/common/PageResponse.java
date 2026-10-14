package com.portfolio.roombooking.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, frontend-friendly pagination wrapper used by {@code GET /api/bookings} (admin list).
 *
 * <p>Why not return Spring's {@link Page} directly? Its JSON shape is an implementation detail that
 * has changed between Spring versions (and Spring now logs a warning when you serialize it).
 * Our own record gives the Angular {@code Page<T>} interface a contract that will not move.
 *
 * @param content       the items on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements number of matching items across all pages
 * @param totalPages    number of pages
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Copies the interesting numbers out of a Spring Data {@link Page}. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
