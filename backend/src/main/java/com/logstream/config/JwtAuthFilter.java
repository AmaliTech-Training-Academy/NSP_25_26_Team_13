package com.logstream.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    @Value("${jwt.secret}") private String jwtSecret;
    @Override
    protected void doFilterInternal(HttpServletRequest rq, HttpServletResponse rs, FilterChain ch) throws ServletException, IOException {
        String h = rq.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                String tk = h.substring(7);
                Claims cl = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(tk).getPayload();
                String em = cl.getSubject();
                String rl = cl.get("role", String.class);
                var a = new UsernamePasswordAuthenticationToken(
                    em, null, List.of(new SimpleGrantedAuthority("ROLE_" + rl)));
                SecurityContextHolder.getContext().setAuthentication(a);
            } catch (Exception e) { SecurityContextHolder.clearContext(); }
        }
        ch.doFilter(rq, rs);
    }
}
