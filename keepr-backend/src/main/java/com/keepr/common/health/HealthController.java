package com.keepr.common.health;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check endpoint for liveness and readiness probes.
 */
@RestController
public class HealthController {

    /**
     * Returns a simple health status indicating the application is running.
     *
     * @return HTTP 200 with status UP and current sprint number
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "sprint", 1
        ));
    }
}
