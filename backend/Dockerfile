FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy build metadata first for better layer caching.
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./mvnw
RUN chmod +x ./mvnw

# Copy source and build the executable Spring Boot jar.
COPY src ./src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=dashboard
ENV SERVER_ADDRESS=0.0.0.0

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]


