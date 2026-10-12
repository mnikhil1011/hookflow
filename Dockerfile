# Stage 1: build the jar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the build files first, so Docker can cache the downloaded dependencies between builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Strip Windows line endings, in case the repository was checked out on Windows.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Stage 2: a small runtime image with only the JRE and the jar.
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as an unprivileged user.
RUN useradd --system --no-create-home hookflow
USER hookflow

COPY --from=build /workspace/target/hookflow-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
