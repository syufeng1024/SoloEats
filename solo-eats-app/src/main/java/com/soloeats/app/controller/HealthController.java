package com.soloeats.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 容器探针。
 * /healthz 只证明进程存活，不访问任何外部依赖；
 * /readyz 从 Actuator readiness 组取聚合状态，公开响应只返回聚合值，组件明细仅写日志。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", Status.UP.getCode());
        body.put("service", "solo-eats");
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readiness() {
        HealthComponent readiness = resolveGroup("readiness");
        Status overall = readiness == null ? Status.UNKNOWN : readiness.getStatus();

        Map<String, String> components = new LinkedHashMap<>();
        if (readiness instanceof CompositeHealth composite) {
            composite.getComponents().forEach((name, component) ->
                    components.put(name, component.getStatus().getCode()));
        }

        boolean allUp = Status.UP.equals(overall)
                && !components.isEmpty()
                && components.values().stream().allMatch(Status.UP.getCode()::equals);
        if (!allUp) {
            log.warn("readiness not UP status={} components={}", overall.getCode(), components);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall.getCode());
        body.put("timestamp", OffsetDateTime.now().toString());

        return ResponseEntity.status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private HealthComponent resolveGroup(String path) {
        try {
            return healthEndpoint.healthForPath(path);
        } catch (RuntimeException ex) {
            log.warn("health group unavailable path={} reason={}", path, ex.getMessage());
            return null;
        }
    }
}
