FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-contracts/pom.xml common-contracts/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY podcast-excerpt-service/pom.xml podcast-excerpt-service/pom.xml
COPY guest-release-service/pom.xml guest-release-service/pom.xml
COPY distribution-task-service/pom.xml distribution-task-service/pom.xml
RUN mvn -B -q -DskipTests dependency:resolve || true
COPY common-contracts/src common-contracts/src
COPY discovery-server/src discovery-server/src
COPY api-gateway/src api-gateway/src
COPY podcast-excerpt-service/src podcast-excerpt-service/src
COPY guest-release-service/src guest-release-service/src
COPY distribution-task-service/src distribution-task-service/src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/discovery-server/target/discovery-server-1.0.0.jar /app/discovery-server.jar
COPY --from=build /workspace/api-gateway/target/api-gateway-1.0.0.jar /app/api-gateway.jar
COPY --from=build /workspace/podcast-excerpt-service/target/podcast-excerpt-service-1.0.0.jar /app/podcast-excerpt-service.jar
COPY --from=build /workspace/guest-release-service/target/guest-release-service-1.0.0.jar /app/guest-release-service.jar
COPY --from=build /workspace/distribution-task-service/target/distribution-task-service-1.0.0.jar /app/distribution-task-service.jar
EXPOSE 8080 8081 8082 8083 8761
CMD ["java", "-jar", "/app/discovery-server.jar"]
