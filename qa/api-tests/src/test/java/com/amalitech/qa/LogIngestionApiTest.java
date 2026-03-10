package com.amalitech.qa;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class LogIngestionApiTest extends BaseTest {

    @Test
    public void testIngestSingleLog() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("{\"level\":\"INFO\",\"source\":\"test-service\",\"message\":\"Test log entry\",\"serviceName\":\"test-service\"}")
                .when().post("/api/logs")
                .then().statusCode(200);
    }

    @Test
    public void testSearchLogs() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("{\"level\":\"INFO\"}")
                .when().post("/api/logs/search")
                .then().statusCode(200);
    }

    @Test
    public void testGetAnalytics() {
        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/logs/analytics")
                .then().statusCode(200);
    }

    @Test
    public void testBatchIngestion() {
        String payload = """
                    {
                      "logs": [
                        {
                          "serviceName": "auth-service",
                          "timestamp": "2023-11-01T10:00:00Z",
                          "level": "INFO",
                          "message": "User login successful"
                        },
                        {
                          "serviceName": "payment-service",
                          "timestamp": "2023-11-01T10:05:00Z",
                          "level": "ERROR",
                          "message": "Payment timeout"
                        }
                      ]
                    }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .when().post("/api/logs/batch")
                .then()
                .statusCode(anyOf(is(200), is(201))) // Allow 200 or 201 based on backend
                .body("ingested", notNullValue());
    }

    @Test
    public void testSearchByDateRange() {
        String searchPayload = """
                    {
                      "startTime": "2023-01-01T00:00:00Z",
                      "endTime": "2025-01-01T00:00:00Z"
                    }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body(searchPayload)
                .when().post("/api/logs/search")
                .then().statusCode(200);
    }

    @Test
    public void testSearchPagination() {
        String searchPayload = """
                    {
                      "page": 1,
                      "size": 5
                    }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body(searchPayload)
                .when().post("/api/logs/search")
                .then()
                .statusCode(200)
                .body("pageable.pageNumber", equalTo(1))
                .body("pageable.pageSize", equalTo(5));
    }

    @Test
    public void testRetentionPolicies() {
        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/retention")
                .then()
                .statusCode(200);
    }
}
