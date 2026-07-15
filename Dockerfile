FROM docker.1ms.run/eclipse-temurin:17-jre
WORKDIR /app

# Install Playwright browser system dependencies + Chinese fonts
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0t64 libnss3 libnspr4 libdbus-1-3 \
    libatk1.0-0t64 libatk-bridge2.0-0t64 libatspi2.0-0t64 \
    libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 \
    libxrandr2 libgbm1 libdrm2 libxcb1 libxkbcommon0 libasound2t64 \
    libcairo2 libcups2t64 libpango-1.0-0 \
    fonts-noto-cjk fonts-wqy-zenhei \
    && rm -rf /var/lib/apt/lists/*

# ---------- Environment variables (defaults from application.yml / application-prod.yml) ----------
# Profile
ENV SPRING_PROFILES_ACTIVE=prod

# Database (application-prod.yml)
ENV MYSQL_HOST=mysql
ENV MYSQL_PORT=3306
ENV MYSQL_DB=scheduled_task
ENV MYSQL_USER=root
ENV MYSQL_PASSWORD=123456

# Security (application.yml)
ENV JWT_SECRET=scheduled-task-secret-key-change-in-production
ENV SCHEDULED_TASK_AES_KEY=ScheduledTask#01
ENV API_KEY=Tn2Y*ggwolgQ5iVIt4JHl!ZzQNEelF*b

# CORS (application-prod.yml)
ENV CORS_ALLOWED_ORIGINS=https://127.0.0.1:1236

COPY target/scheduled-task-1.0.0-SNAPSHOT.jar app.jar

# Pre-install Playwright Chromium and system dependencies during image build.
# This keeps the runtime path fast; the manual install API remains as a fallback.
RUN java -Dloader.main=com.microsoft.playwright.CLI -jar app.jar install chromium \
    && java -Dloader.main=com.microsoft.playwright.CLI -jar app.jar install-deps chromium

EXPOSE 1236
ENTRYPOINT ["java", "-jar", "app.jar"]
