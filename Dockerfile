# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --chown=app:app build/libs/app.jar app.jar

# 배포환경에서는 S3를 쓰기 때문에 로컬에서만 사용한다.
RUN mkdir -p /app/uploads && chown -R app:app /app/uploads

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]