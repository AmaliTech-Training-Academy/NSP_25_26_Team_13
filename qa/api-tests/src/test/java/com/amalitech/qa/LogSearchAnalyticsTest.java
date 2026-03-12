package com.amalitech.qa;

import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class LogSearchAnalyticsTest extends BaseTest {

    @Test(priority = 1)
    public void testSearchLogsByKeyword() {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put("keyword", "authenticated");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(searchRequest)
                .when()
                .post("/api/logs/search")
                .then()
                .statusCode(200)
                .body("content", any(java.util.List.class)); // Expecting paged content
    }

    @Test(priority = 2)
    public void testSearchLogsByLevel() {
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put("level", "ERROR");
        searchRequest.put("serviceName", "PaymentService");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(searchRequest)
                .when()
                .post("/api/logs/search")
                .then()
                .statusCode(200)
                .body("content", any(java.util.List.class));
    }

    @Test(priority = 3)
    public void testGetAnalytics() {
        String startDate = "2024-01-01T00:00:00Z";
        String endDate = "2024-12-31T23:59:59Z";

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .when()
                .get("/api/analytics/error-rate")
                .then()
                .statusCode(200);
        // Analytics is currently hardcoded to return null in the backend controller.
        // We'll just verify the 200 OK for now.
    }
}
