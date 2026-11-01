FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Build backend from monorepo root context.
COPY backend/pom.xml ./backend/pom.xml
COPY backend/.mvn ./backend/.mvn
COPY backend/mvnw ./backend/mvnw
COPY backend/src ./backend/src
RUN chmod +x ./backend/mvnw && cd backend && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/backend/target/*.jar /app/app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=dashboard
ENV SERVER_ADDRESS=0.0.0.0

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]

