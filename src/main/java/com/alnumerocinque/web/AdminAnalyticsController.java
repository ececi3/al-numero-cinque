package com.alnumerocinque.web;

import com.alnumerocinque.service.AnalyticsService;
import com.alnumerocinque.web.dto.AnalyticsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Analytics per l'admin, riservato al ruolo ADMIN (vedi SecurityConfig). */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public AnalyticsResponse analytics() {
        return analyticsService.calcola();
    }
}
