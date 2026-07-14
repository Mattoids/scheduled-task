FROM eclipse-temurin:17-jre
WORKDIR /app

# Install Playwright Chromium system dependencies + Chinese fonts
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0t64 libnss3 libnspr4 libdbus-1-3 \
    libatk1.0-0t64 libatk-bridge2.0-0t64 libatspi2.0-0t64 \
    libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 \
    libxrandr2 libgbm1 libdrm2 libxcb1 libxkbcommon0 libasound2t64 \
    fonts-noto-cjk fonts-wqy-zenhei \
    && rm -rf /var/lib/apt/lists/*

COPY scheduled-task-*.jar app.jar
EXPOSE 1236
ENTRYPOINT ["java", "-jar", "app.jar"]
