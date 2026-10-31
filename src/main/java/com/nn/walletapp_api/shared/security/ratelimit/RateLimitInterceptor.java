package com.nn.walletapp_api.shared.security.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String X_RATELIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String ANONYMOUS_PREFIX = "ip:";
    private static final String USER_PREFIX = "usr:";

    private static final long DEFAULT_READ_COST = 1L;
    private static final long STATUS_MUTATION_COST = 2L;
    private static final long TRANSACTION_MUTATION_COST = 5L;

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!rateLimitingService.isEnabled()) {
            return true;
        }

        String clientKey = resolveClientKey(request);
        long tokenCost = resolveTokenCost(request.getMethod(), request.getRequestURI());

        ConsumptionProbe probe = rateLimitingService.tryConsume(clientKey, tokenCost);

        if (probe.isConsumed()) {
            response.addHeader(X_RATELIMIT_REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        handleLimitExceeded(request, response, probe);
        return false;
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return Optional.ofNullable(authentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(jwt -> USER_PREFIX + jwt.getToken().getSubject())
                .orElseGet(() -> ANONYMOUS_PREFIX + resolveClientIp(request));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .filter(header -> !header.isBlank())
                .map(header -> header.split(",")[0].trim())
                .orElseGet(request::getRemoteAddr);
    }

    private long resolveTokenCost(String method, String uri) {
        if ("POST".equalsIgnoreCase(method) && uri.startsWith("/api/v1/transactions/")) {
            return TRANSACTION_MUTATION_COST;
        }
        if ("PATCH".equalsIgnoreCase(method) && uri.matches("^/api/v1/admin/wallets/[^/]+/status$")) {
            return STATUS_MUTATION_COST;
        }
        return DEFAULT_READ_COST;
    }

    private void handleLimitExceeded(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ConsumptionProbe probe) throws IOException {
        long nanosToWaitForRefill = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill));

        log.warn("Rate limit exceeded for client from IP: {}. Retry-After: {}s",
                request.getRemoteAddr(), retryAfterSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Quota exceeded. Please retry after " + retryAfterSeconds + " seconds."
        );
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://api.walletapp.com/errors/rate-limit-exceeded"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now(clock).toString());

        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}