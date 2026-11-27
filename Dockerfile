# 실행 이미지. jar는 로컬에서 미리 만든다 (서버나 이미지 안에서 Gradle·npm 빌드를 돌리지 않는다).
#   ./gradlew bootJar -PwithFrontend
#   docker buildx build --platform linux/arm64 --build-arg VERSION=0.11.0 -t vcg258/renova-delve:0.11.0 --push .
# 자세한 순서는 docs/deploy.md
FROM eclipse-temurin:21-jre

ARG VERSION=0.11.0

RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY build/libs/RenovaNDelve-${VERSION}.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod
# 컨테이너 메모리 상한(compose의 mem_limit)의 75%를 힙으로 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
