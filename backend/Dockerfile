FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add wget for the HEALTHCHECK below (not present in alpine by default)
RUN apk add --no-cache wget

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# /swagger-ui/index.html is explicitly permitted in SecurityConfig (.requestMatchers("/swagger-ui/**"))
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD wget --quiet --spider http://localhost:8080/swagger-ui/index.html || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
