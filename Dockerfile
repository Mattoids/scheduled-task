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

# ---------- Environment variables (defaults from application.yml / application-prod.yml) ----------
# Profile
ENV SPRING_PROFILES_ACTIVE=prod

# Database (application-prod.yml)
ENV MYSQL_HOST=mysql-5-7-cluster-master
ENV MYSQL_PORT=3306
ENV MYSQL_DB=scheduled_task
ENV MYSQL_USER=root
ENV MYSQL_PASSWORD=mysql_E8HEYi

# Security (application.yml)
ENV JWT_SECRET=scheduled-task-secret-key-change-in-production
ENV SCHEDULED_TASK_AES_KEY=ScheduledTask#01
ENV API_KEY=

# CORS (application-prod.yml)
ENV CORS_ALLOWED_ORIGINS=https://scheduled.mattoid.cn:55554,https://scheduled.mattoid.cn:55555,https://scheduled.mattoid.cn:55556

COPY scheduled-task-*.jar app.jar
EXPOSE 1236
ENTRYPOINT ["java", "-jar", "app.jar"]
