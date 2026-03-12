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
        .body(
            "{\"level\":\"INFO\",\"source\":\"test-service\",\"message\":\"Test log entry\",\"serviceName\":\"test-service\"}")
        .when().post("/api/logs")
        .then().statusCode(201);
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

  // @Test
  // public void testGetAnalytics() {
  //   given()
  //       .header("Authorization", "Bearer " + token)
  //       .when().get("/api/logs/analytics")
  //       .then().statusCode(200);
  // }

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
        .statusCode(201) // Allow 200 or 201 based on backend
        .body("success", equalTo(true));
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

  @Test
  public void testLargeBatchIngestion() {
    StringBuilder logs = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      logs.append(String.format("""
          {
            "serviceName": "stress-test-%d",
            "level": "INFO",
            "message": "Stress test log message %d"
          }""", i, i));
      if (i < 49)
        logs.append(",");
    }
    String payload = "{\"logs\": [" + logs.toString() + "]}";

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(payload)
        .when().post("/api/logs/batch")
        .then()
        .statusCode(anyOf(is(200), is(201)))
        .body("success", equalTo(true));
  }

  @Test
  public void testPaginationBeyondRange() {
    String searchPayload = """
            {
              "page": 1000,
              "size": 10
            }
        """;

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(searchPayload)
        .when().post("/api/logs/search")
        .then()
        .statusCode(200)
        .body("content", hasSize(0));
  }

  @Test
  public void testMissingTimestampDefaultsToNow() {
    String payload = """
        {
          "serviceName": "timestamp-test",
          "level": "INFO",
          "message": "Testing default timestamp"
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(payload)
        .when().post("/api/logs")
        .then()
        .statusCode(201)
        .body("timestamp", notNullValue());
  }

  @Test
  public void testSearchResponseContract() {
    // Advanced QA: Verify that the API response strictly follows the expected
    // contract/schema
    java.util.Map<String, Object> searchPayload = new java.util.HashMap<>();
    searchPayload.put("page", 0);
    searchPayload.put("size", 5);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(searchPayload)
        .when().post("/api/logs/search")
        .then()
        .statusCode(200)
        .body("content", instanceOf(java.util.List.class))
        .body("totalElements", anyOf(instanceOf(Integer.class), instanceOf(Long.class)))
        .body("totalPages", anyOf(instanceOf(Integer.class), instanceOf(Long.class)))
        .body("pageable", notNullValue())
        .body("content[0].id", notNullValue())
        .body("content[0].serviceName", notNullValue())
        .body("content[0].level", notNullValue())
        .body("content[0].message", notNullValue())
        .body("content[0].timestamp", notNullValue());
  }
}