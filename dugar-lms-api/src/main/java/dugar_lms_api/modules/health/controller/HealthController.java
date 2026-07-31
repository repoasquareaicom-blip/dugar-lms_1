package dugar_lms_api.modules.health.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Dugar LMS API is running");
        response.put("application", "Dugar LMS API");
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}