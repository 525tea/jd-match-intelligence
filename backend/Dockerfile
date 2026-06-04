FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --uid 10001 appuser

COPY --from=build /workspace/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
