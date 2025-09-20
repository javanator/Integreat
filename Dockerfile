## ---- BUILD STAGE ----
FROM ubuntu AS builder
RUN curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
RUN apt update
RUN apt install -y nodejs npm
RUN apt install -y openjdk-21-jdk

WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src
COPY frontend ./frontend

WORKDIR /app
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon --no-configuration-cache build

## ---- RUNTIME STAGE ----
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Run the application
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
