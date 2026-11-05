# ============================================================
# PulseStream Backend — Dockerfile
# Multi-stage build: keeps the final image lean & production-ready
# ============================================================

# ──────────────────────────────────────────────────────────────
# STAGE 1: BUILD
# Uses Maven + JDK 21 to compile the Spring Boot fat JAR
# ──────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Pro-move: copy pom.xml first so Maven dependencies are cached
# (Docker only re-downloads deps when pom.xml changes, not src/)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source code and build the fat JAR (skip tests for speed)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ──────────────────────────────────────────────────────────────
# STAGE 2: RUNTIME
# Uses a minimal JRE image + installs FFmpeg for HLS chunking
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 🎬 CRITICAL: Install FFmpeg — without this, video chunking WILL crash
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create the temp directory Spring Boot uses for HLS segments
# (matches java.io.tmpdir + /PulseStream in ContentServiceImpl.java)
RUN mkdir -p /tmp/PulseStream && chmod 777 /tmp/PulseStream

#  Copy the compiled JAR from Stage 1
COPY --from=build /app/target/streaming-platform-0.0.1-SNAPSHOT.jar app.jar

#  Expose the Spring Boot default port
EXPOSE 8080

# Run the app with memory-safe JVM flags for cloud deployment
# -Xmx400m        → max heap 400MB (safe for Render free tier)
# -XX:+UseG1GC    → G1 garbage collector (better for streaming workloads)
# -Djava.io.tmpdir → explicitly set temp dir for HLS file creation
ENTRYPOINT ["java", \
  "-Xmx400m", \
  "-XX:+UseG1GC", \
  "-Djava.io.tmpdir=/tmp", \
  "-jar", "app.jar"]
