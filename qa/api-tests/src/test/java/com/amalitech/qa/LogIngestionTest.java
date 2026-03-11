package com.amalitech.qa;

import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class LogIngestionTest extends BaseTest {

    @Test(priority = 1)
    public void testSuccessfulLogIngestion() {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("serviceName", "AuthService");
        logEntry.put("level", "INFO");
        logEntry.put("message", "User successfully authenticated");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("userId", "12345");
        logEntry.put("metadata", metadata);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(logEntry)
                .when()
                .post("/api/logs")
                .then()
                .statusCode(201)
                .body("success", equalTo(true));
    }

    @Test(priority = 2)
    public void testBatchLogIngestion() {
        List<Map<String, Object>> logs = new ArrayList<>();

        Map<String, Object> log1 = new HashMap<>();
        log1.put("serviceName", "PaymentService");
        log1.put("level", "ERROR");
        log1.put("message", "Payment processing failed");
        logs.add(log1);

        Map<String, Object> log2 = new HashMap<>();
        log2.put("serviceName", "PaymentService");
        log2.put("level", "WARN");
        log2.put("message", "Retrying payment for user 888");
        logs.add(log2);

        Map<String, Object> batchRequest = new HashMap<>();
        batchRequest.put("logs", logs);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(batchRequest)
                .when()
                .post("/api/logs/batch")
                .then()
                .statusCode(201)
                .body("success", equalTo(true));
    }

    // @Test(priority = 3)
    // public void testMalformedLogIngestion() {
    // Map<String, Object> invalidLogEntry = new HashMap<>();
    // // Missing required fields like 'serviceName', 'level', 'message'
    // invalidLogEntry.put("timestamp", "2024-03-09T12:00:00Z");

    // given()
    // .header("Authorization", "Bearer " + token)
    // .contentType(ContentType.JSON)
    // .body(invalidLogEntry)
    // .when()
    // .post("/api/logs")
    // .then()
    // .statusCode(400); // Bad Request expected
    // }
}
