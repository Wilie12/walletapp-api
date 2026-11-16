# ==============================================================================
# Stage 1: Builder (Compile & Extract Spring Boot Layers)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B -ntp

# Copy application source and build layered JAR artifact
COPY src/ src/
RUN ./mvnw package -DskipTests -B -ntp && \
    cp target/*.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ==============================================================================
# Stage 2: Hardened Runtime (Minimal JRE Alpine + Non-Root User)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create non-privileged system group and user (UID=10001, GID=10001)
RUN addgroup -g 10001 -S appuser && \
    adduser -u 10001 -G appuser -S -D -H -s /sbin/nologin appuser

# Copy extracted layers in order from least to most frequently changing
COPY --from=builder --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=10001:10001 /workspace/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]