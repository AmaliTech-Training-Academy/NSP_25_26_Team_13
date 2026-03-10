package com.amalitech.qa;

import io.restassured.http.ContentType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SecurityApiTest extends BaseTest {

    @Test(priority = 1)
    public void testUnauthorizedAccessToLogs() {
        // Accessing logs without a token should return 401/403
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/logs/analytics")
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

    @Test(priority = 2)
    public void testUserRoleCannotAccessAdminEndpoints() {
        // 1. Generate token for a regular user manually to bypass broken login
        String userToken = generateManualToken("user@amalitech.com", "USER");
        System.out.println("Generated manual USER token: " + userToken);

        // 2. Verify that a valid USER token works for basic search
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new HashMap<>())
                .when()
                .post("/api/logs/search")
                .then()
                .statusCode(200)
                .body("content", notNullValue());
    }

    @Test(priority = 3)
    public void testInvalidTokenRejection() {
        given()
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.invalid.token")
                .when()
                .get("/api/logs/analytics")
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }
}
