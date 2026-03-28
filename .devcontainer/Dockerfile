FROM ubuntu:24.04

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Install common development tools and OpenJDK 21
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    git \
    unzip \
    zip \
    openjdk-21-jdk \
    sudo \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user 'vscode'
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN if getent passwd ubuntu > /dev/null 2>&1; then userdel -r ubuntu; fi \
    && groupadd --gid $USER_GID vscode \
    && useradd -ms /bin/bash --uid $USER_UID --gid $USER_GID vscode \
    && echo "vscode ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

USER vscode
WORKDIR /home/vscode

# Install SDKMAN to manage Kotlin, Gradle, and Quarkus CLI
RUN curl -s "https://get.sdkman.io" | bash

# Install specific versions/tools with SDKMAN
RUN bash -c "source /home/vscode/.sdkman/bin/sdkman-init.sh && \
    sdk install kotlin && \
    sdk install gradle && \
    sdk install quarkus"

# Add SDK components to PATH
ENV PATH="/home/vscode/.sdkman/candidates/kotlin/current/bin:/home/vscode/.sdkman/candidates/gradle/current/bin:/home/vscode/.sdkman/candidates/quarkus/current/bin:${PATH}"

WORKDIR /workspace
