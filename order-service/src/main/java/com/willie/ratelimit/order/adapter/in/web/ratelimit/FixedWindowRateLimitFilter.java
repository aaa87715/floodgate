package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.willie.ratelimit.order.adapter.in.web.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fixed window 限流：把時間切成固定長度的格子，每格一個計數器，滿了就擋，進下一格歸零。
 */
@Component
public class FixedWindowRateLimitFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PREFIX = "/api/";


    private record Window(long startMillis, int count ) {

    }
   

    private final Map<String, AtomicReference<Window>> windows = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FixedWindowRateLimitFilter(RateLimitProperties properties,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
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

        String clientKey = clientKey(request);
        long now = clock.millis();

        long windowMillis = properties.window().toMillis();
        int limit = properties.limit();
        var ref = windows.computeIfAbsent(clientKey , k -> new AtomicReference<>(new Window(now, 0)));
        Window  currentWindow = ref.updateAndGet(window -> {
            if( now - window.startMillis() >= windowMillis) {
                return new Window(now , 1);
            }
            return new Window(window.startMillis(), window.count() + 1);
        });

        boolean allowed = currentWindow.count() <= limit;
        int remaining = allowed ? limit -  currentWindow.count() : 0;
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (!allowed) {
            long windowEnd =  currentWindow.startMillis() + windowMillis;
            rejectWithTooManyRequests(response, Math.max(1, (windowEnd - now) / 1000));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 429 不會經過 GlobalExceptionHandler —— filter 在 DispatcherServlet 之前，
     * @RestControllerAdvice 管不到，所以回應要自己寫。
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
