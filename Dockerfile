FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/dropshipping-search-0.1.0.jar app.jar
EXPOSE 4000
ENTRYPOINT ["java","-jar","/app/app.jar"]


