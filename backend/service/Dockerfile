FROM maven:3.9.12-eclipse-temurin-25 AS builder
LABEL authors="Csaba Csíkos"

WORKDIR /builder

# Incremental docker builds will resume here when you change sources
COPY build_config build_config
COPY pom.xml .
COPY src src
RUN mvn package -DskipTests

# This points to the built jar file in the target folder
ARG JAR_FILE=target/*.jar

# Copy the jar file to the working directory and rename it to application.jar
RUN cp ${JAR_FILE} application.jar

# Extract the jar file using an efficient layout
RUN java -Djarmode=tools -jar application.jar extract --destination extracted

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Create user
RUN addgroup -S ats && adduser -S ats -G ats

# Copy the extracted layers
COPY --chown=ats:ats --from=builder /builder/extracted/lib/ ./lib/
COPY --chown=ats:ats --from=builder /builder/extracted/application.jar ./

USER ats

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders"

EXPOSE 8080 9000

ENTRYPOINT ["java", "-jar", "application.jar"]