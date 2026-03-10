package com.amalitech.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import org.testng.annotations.BeforeSuite;

import javax.crypto.SecretKey;
import java.util.Date;

public class BaseTest {

    protected static String token;
    private static final String BASE_URL = System.getenv("API_BASE_URL") != null ? System.getenv("API_BASE_URL")
            : "http://localhost:8080";
    private static final String JWT_SECRET = "logstream-secret-key-amalitech-2024-secure";
    private static final long EXPIRATION = 86400000; // 24 hours

    @BeforeSuite
    public void setup() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.defaultParser = io.restassured.parsing.Parser.JSON;

        // Bypassing broken login endpoint by generating token manually
        // This allows us to continue testing other API endpoints while Auth is fixed.
        token = generateManualToken("admin@amalitech.com", "ADMIN");
        System.out.println("Generated manual token for testing: " + token);
    }

    protected String generateManualToken(String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }
}
