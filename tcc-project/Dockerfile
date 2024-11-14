# Stage 1: Build the application
FROM maven:3.9.4-eclipse-temurin-11 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml /app/
RUN mvn dependency:go-offline

# Copy the source code
COPY src /app/src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/ttc-client-1.0-SNAPSHOT.jar /app/ttc-client.jar

# Expose the UDP ports
EXPOSE 5001/udp 5002/udp 5003/udp 5004/udp

# Set environment variables (can be overridden in docker-compose)
ENV CLIENT_ID=1
ENV WISHLIST=2,3,4
ENV CLIENT_ADDRESSES=client1:1,client2:2,client3:3,client4:4

# Run the application
CMD ["java", "-cp", "/app/ttc-client.jar", "org.example.Client.Client"]
