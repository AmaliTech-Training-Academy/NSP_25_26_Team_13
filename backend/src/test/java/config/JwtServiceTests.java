package config;

import com.logstream.config.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);
    }

    @Test
    void shouldGenerateAndValidateToken() {
        String email = "test@example.com";
        String role = "USER";

        String token = jwtService.generateToken(email, role);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void shouldExtractEmailFromToken() {
        String email = "test@example.com";

        String token = jwtService.generateToken(email, "USER");

        String extractedEmail = jwtService.extractEmail(token);

        assertEquals(email, extractedEmail);
    }

    @Test
    void shouldExtractClaimsFromToken() {
        String email = "test@example.com";
        String role = "ADMIN";

        String token = jwtService.generateToken(email, role);

        Claims claims = jwtService.extractClaims(token);

        assertEquals(email, claims.getSubject());
        assertEquals(role, claims.get("role"));
    }

    @Test
    void shouldReturnFalseForExpiredToken() throws InterruptedException {
        JwtService shortExpiryService = new JwtService();

        ReflectionTestUtils.setField(shortExpiryService, "secret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(shortExpiryService, "expiration", 1L); // 1 ms

        String token = shortExpiryService.generateToken("test@example.com", "USER");

        Thread.sleep(10);

        assertFalse(shortExpiryService.isTokenValid(token));
    }

    @Test
    void shouldReturnFalseForInvalidSignature() {
        String token = jwtService.generateToken("test@example.com", "USER");

        JwtService anotherService = new JwtService();
        ReflectionTestUtils.setField(anotherService, "secret", "DIFFERENT_SECRET_KEY_123456789012345");
        ReflectionTestUtils.setField(anotherService, "expiration", 3600000L);

        assertFalse(anotherService.isTokenValid(token));
    }
}