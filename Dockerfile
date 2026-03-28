ARG BASE_IMAGE=ubuntu:25.10
FROM ${BASE_IMAGE} AS builder

ENV TZ="Europe/Vienna" \
    DEBIAN_FRONTEND="noninteractive"

# Update apt, install tzdata and openjdk, then configure the timezone
RUN apt-get update && \
    apt-get install -y tzdata openjdk-25-jdk python3 && \
    ln -fs /usr/share/zoneinfo/$TZ /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN ./gradlew build -Dquarkus.package.jar.type=uber-jar

RUN mv build/rest-kotlin-quickstart*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
