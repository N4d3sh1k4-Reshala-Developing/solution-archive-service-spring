FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

ARG GHP_USER
ARG GHP_TOKEN
ENV GHP_USER=$GHP_USER
ENV GHP_TOKEN=$GHP_TOKEN

COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN ./gradlew bootJar -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8185
ENTRYPOINT ["java", "-jar", "app.jar"]