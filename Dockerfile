# Use an image with JDK 21
FROM eclipse-temurin:21-jdk-jammy

# Define environment variables for Android SDK
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH $PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Install necessary system packages
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK Command Line Tools
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools \
    && wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/tools.zip \
    && unzip /tmp/tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools \
    && mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest \
    && rm /tmp/tools.zip

# Accept Android SDK licenses
RUN yes | sdkmanager --licenses

# Install specific SDK components (matching build.gradle.kts)
RUN sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# Set working directory
WORKDIR /workspace

# Copy the project files
COPY . .

# Ensure gradlew is executable
RUN chmod +x ./gradlew

# Default command (can be overridden in Jenkins)
CMD ["./gradlew", "assembleDebug"]
