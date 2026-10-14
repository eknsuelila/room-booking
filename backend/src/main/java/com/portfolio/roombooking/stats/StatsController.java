package com.portfolio.roombooking.stats;

import com.portfolio.roombooking.stats.dto.StatsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/stats} - admin dashboard figures.
 *
 * <p>Protected twice on purpose ("defence in depth"): a URL rule in SecurityConfig and
 * {@code @PreAuthorize} here. If one is removed by mistake, the other still blocks regular users.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public StatsResponse stats() {
        return service.stats();
    }
}
