package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.willie.ratelimit.order.adapter.in.web.ErrorResponse;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimiter.Decision;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PREFIX = "/api/";

    private final RateLimiter rateLimit;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimit,
                                      RateLimitProperties properties,
                                      ObjectMapper objectMapper) {
        this.rateLimit = rateLimit;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 關掉限流、或不是 /api/** 的請求（含 /actuator/health）都跳過
        return !properties.enabled() || !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Decision decision = rateLimit.tryAcquire(clientKey(request));

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            rejectWithTooManyRequests(response, decision.retryAfter().toMillis());
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 429 不會經過 GlobalExceptionHandler —— filter 在 DispatcherServlet 之前，
     * @RestControllerAdvice 管不到，所以回應要自己寫
     */
    private void rejectWithTooManyRequests(HttpServletResponse response,long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = new ErrorResponse("too_many_requests", "Too many requests. Please try again later.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
