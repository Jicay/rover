FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies --configuration compileClasspath > /dev/null

COPY config config
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install --assume-yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
