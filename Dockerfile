# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

# Copy Gradle metadata first so dependency downloads stay cached when source changes.
COPY gradlew ./gradlew
COPY gradle/wrapper/ ./gradle/wrapper/
COPY settings.gradle* build.gradle* gradle.properties* ./
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -q help

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test && \
    JAR_FILE=$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit) && \
    test -n "$JAR_FILE" && \
    mv "$JAR_FILE" app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8"

COPY --from=builder /workspace/app.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
