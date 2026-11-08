# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre

WORKDIR /app

# DB 준비 상태 확인에 curl을 사용하고, 비밀값 파일을 읽는 그룹 ID를 고정한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app

COPY --chown=app:app build/libs/app.jar app.jar

# 배포환경에서는 S3를 쓰기 때문에 로컬에서만 사용한다.
RUN mkdir -p /app/uploads && chown -R app:app /app/uploads

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]