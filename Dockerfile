# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon && \
    JAR_FILE=$(find build/libs -name "*.jar" ! -name "*-plain.jar" | head -n 1) && \
    cp "$JAR_FILE" app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=builder --chown=app:app /app/app.jar app.jar

# 배포환경에서는 S3를 쓰기 때문에 로컬에서만 사용한다.
RUN mkdir -p /app/uploads && chown -R app:app /app/uploads

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]