package com.nn.walletapp_api.shared.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@RestController
@Profile("benchmark")
@RequestMapping("/api/v1/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final Clock clock;

    @GetMapping("/baseline")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> getBaseline() {
        return Map.of(
                "variant", "A_BASELINE",
                "status", "OK",
                "timestamp", Instant.now(clock).toString()
        );
    }
}