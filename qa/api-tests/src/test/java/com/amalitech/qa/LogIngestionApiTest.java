package com.amalitech.qa;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class LogIngestionApiTest {
    private String authToken;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = "http://localhost:8080";
        authToken = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"admin@amalitech.com\",\"password\":\"password123\"}")
        .when().post("/api/auth/login").then().extract().path("token");
    }

    @Test
    public void testIngestSingleLog() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authToken)
            .body("{\"level\":\"INFO\",\"source\":\"test-service\",\"message\":\"Test log entry\",\"serviceName\":\"test-service\"}")
        .when().post("/api/logs")
        .then().statusCode(200);
    }

    @Test
    public void testSearchLogs() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authToken)
            .body("{\"level\":\"INFO\"}")
        .when().post("/api/logs/search")
        .then().statusCode(200);
    }

    @Test
    public void testGetAnalytics() {
        given()
            .header("Authorization", "Bearer " + authToken)
        .when().get("/api/logs/analytics")
        .then().statusCode(200);
    }

    // TODO: testBatchIngestion
    // TODO: testSearchByDateRange
    // TODO: testRetentionPolicies
}
