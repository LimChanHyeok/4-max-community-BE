package org.example.community.global.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("ALIVE");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> readiness() {
        try (Connection connection = dataSource.getConnection()) {

            if (!connection.isValid(2)) {
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("NOT_READY");
            }

            return ResponseEntity.ok("READY");

        } catch (SQLException exception) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("NOT_READY");
        }
    }
}