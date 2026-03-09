package com.logstream.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@DisplayName("AnalyticsController Tests")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Autowired
    private ObjectMapper objectMapper;

    private List<ErrorRateResponse> sampleErrorRates;

    @BeforeEach
    void setUp() {
        sampleErrorRates = Arrays.asList(
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
                .build(),
            ErrorRateResponse.builder()
                .service("user-service")
                .errorRate(12.5)
                .errorCount(25L)
                .totalCount(200L)
                .build()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("Should return error rates successfully for authenticated user")
    void getErrorRate_shouldReturnErrorRates() throws Exception {
        when(analyticsService.getErrorRatePerService()).thenReturn(sampleErrorRates);

        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Error rates retrieved successfully"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].service").value("auth-service"))
            .andExpect(jsonPath("$.data[0].errorRate").value(5.2))
            .andExpect(jsonPath("$.data[0].errorCount").value(52))
            .andExpect(jsonPath("$.data[0].totalCount").value(1000))
            .andExpect(jsonPath("$.data[1].service").value("payment-service"))
            .andExpect(jsonPath("$.data[1].errorRate").value(0.0))
            .andExpect(jsonPath("$.data[1].errorCount").value(0))
            .andExpect(jsonPath("$.data[1].totalCount").value(500))
            .andExpect(jsonPath("$.data[2].service").value("user-service"))
            .andExpect(jsonPath("$.data[2].errorRate").value(12.5))
            .andExpect(jsonPath("$.data[2].errorCount").value(25))
            .andExpect(jsonPath("$.data[2].totalCount").value(200));
    }
    
    @Test
    @DisplayName("Should return 401 Unauthorized for unauthenticated request")
    void getErrorRate_shouldReturnUnauthorizedForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should return empty list when no services exist")
    void getErrorRate_shouldReturnEmptyListWhenNoServices() throws Exception {
        when(analyticsService.getErrorRatePerService()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Error rates retrieved successfully"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }
    
    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("Should return error rates for admin user")
    void getErrorRate_shouldReturnErrorRatesForAdminUser() throws Exception {
        when(analyticsService.getErrorRatePerService()).thenReturn(sampleErrorRates);

        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(3));
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should return correct API response structure")
    void getErrorRate_shouldReturnCorrectApiResponseStructure() throws Exception {
        when(analyticsService.getErrorRatePerService()).thenReturn(sampleErrorRates);

        String responseJson = mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        
        ApiResponse<?> response = objectMapper.readValue(responseJson, ApiResponse.class);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Error rates retrieved successfully");
        assertThat(response.getData()).isNotNull();
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should handle single service response")
    void getErrorRate_shouldHandleSingleServiceResponse() throws Exception {
        List<ErrorRateResponse> singleService = Arrays.asList(
            ErrorRateResponse.builder()
                .service("single-service")
                .errorRate(100.0)
                .errorCount(10L)
                .totalCount(10L)
                .build()
        );
        
        when(analyticsService.getErrorRatePerService()).thenReturn(singleService);

        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].service").value("single-service"))
            .andExpect(jsonPath("$.data[0].errorRate").value(100.0))
            .andExpect(jsonPath("$.data[0].errorCount").value(10))
            .andExpect(jsonPath("$.data[0].totalCount").value(10));
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should handle decimal error rates correctly")
    void getErrorRate_shouldHandleDecimalErrorRatesCorrectly() throws Exception {
        List<ErrorRateResponse> decimalRates = Arrays.asList(
            ErrorRateResponse.builder()
                .service("precision-service")
                .errorRate(33.33)
                .errorCount(1L)
                .totalCount(3L)
                .build()
        );
        
        when(analyticsService.getErrorRatePerService()).thenReturn(decimalRates);

        mockMvc.perform(get("/api/analytics/error-rate")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].errorRate").value(33.33));
    }
}
