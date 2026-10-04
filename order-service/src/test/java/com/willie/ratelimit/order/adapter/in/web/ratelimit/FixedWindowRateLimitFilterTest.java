package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ratelimit.orders.enabled=true",
        "ratelimit.orders.limit=5",
        "ratelimit.orders.window=1m"
})
public class FixedWindowRateLimitFilterTest {

    private static final long T0 = 1_700_000_000_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private ResultActions performIp(String ip) throws Exception {
        return mockMvc.perform(
                get("/api/orders").header("X-Forwarded-For", ip));
    }

    @Test
    void fixedWindowAllowedDoubleTheLimitAcossTheBoundary() throws Exception {
        given(clock.millis()).willReturn(T0);
        for (int i = 0; i < 5; i++) {
            performIp("203.0.113.7").andExpect(status().isOk());
        }
        performIp("203.0.113.7").andExpect(status().isTooManyRequests());
        given(clock.millis()).willReturn(T0 + 61_000); // 1m1s 後，進入下一個 window
        for (int i = 0; i < 5; i++) {
            performIp("203.0.113.7").andExpect(status().isOk());
        }
    }

@Test
void sixthRequestWithinWindowIsRejected() throws Exception {
    given(clock.millis()).willReturn(T0);
    for (int i = 0; i < 5; i++) {
        performIp("198.51.100.9").andExpect(status().isOk());
    }
    performIp("198.51.100.9")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("X-RateLimit-Remaining", "0"))
            .andExpect(jsonPath("$.errorCode").value("too_many_requests"));
}

}
