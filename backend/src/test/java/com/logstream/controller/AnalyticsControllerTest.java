package com.logstream.controller;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    @WithMockUser
    void getErrorRate_shouldReturnErrorRates() throws Exception {
        List<ErrorRateResponse> mockResponse = Arrays.asList(
            ErrorRateResponse.builder()
                .service("auth-service")
                .errorRate(5.2)
                .errorCount(52L)
                .totalCount(1000L)
                .build(),
            ErrorRateResponse.builder()
                .service("payment-service")
                .errorRate(0.0)
                .errorCount(0L)
                .totalCount(500L)
                .build()
        );

        when(analyticsService.getErrorRateByService()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/error-rate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].service").value("auth-service"))
            .andExpect(jsonPath("$[0].errorRate").value(5.2))
            .andExpect(jsonPath("$[0].errorCount").value(52))
            .andExpect(jsonPath("$[0].totalCount").value(1000))
            .andExpect(jsonPath("$[1].service").value("payment-service"))
            .andExpect(jsonPath("$[1].errorRate").value(0.0));
    }
}
