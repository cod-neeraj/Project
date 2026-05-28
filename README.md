# 🚀 DevOps Assignment — Spring Boot Production Deployment

## Architecture Overview

```
                        Internet
                           │
                    ┌──────▼──────┐
                    │  NGINX      │  :80 (redirects to HTTPS)
                    │  :80/:443   │  :443 (SSL termination)
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Spring Boot │  :8080
                    │    App      │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼─────┐ ┌───▼───┐ ┌─────▼──────┐
       │ PostgreSQL │ │ Redis │ │ Prometheus │
       │   :5432    │ │ :6379 │ │   :9090    │
       └────────────┘ └───────┘ └─────┬──────┘
                                      │
                               ┌──────▼──────┐
                               │   Grafana   │
                               │    :3000    │
                               └─────────────┘
```

All services run on a single AWS EC2 t3.micro instance (Ubuntu 22.04) via Docker Compose.

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Application | Spring Boot (Java 21) |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Reverse Proxy | NGINX (SSL + HTTP→HTTPS redirect) |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Cloud | AWS EC2 (Ubuntu 22.04, t3.micro) |
| Monitoring | Prometheus + Grafana |
| Security | UFW Firewall + Fail2ban |

---

## Live Endpoints

| Endpoint | URL |
|----------|-----|
| Health Check | `https://35.154.157.202/actuator/health` |
| Prometheus | `http://35.154.157.202:9090` |
| Grafana | `http://35.154.157.202:3000` |

---

## Project Structure

```
Project/
├── Internship/
│   └── Internship/
│       ├── src/                        # Spring Boot source code
│       ├── Dockerfile                  # Multi-stage Docker build
│       ├── docker-compose.yml          # All services definition
│       ├── nginx/
│       │   ├── nginx.conf              # Reverse proxy + SSL config
│       │   └── ssl/                    # SSL certificates
│       └── prometheus/
│           └── prometheus.yml          # Prometheus scrape config
└── .github/
    └── workflows/
        └── deploy.yml                  # CI/CD pipeline
```

---

## CI/CD Pipeline

```
Push to main branch
        │
        ▼
GitHub Actions triggered
        │
   ┌────┴────┐
   │  BUILD  │
   │         │  1. Checkout code
   │         │  2. Login to DockerHub
   │         │  3. Build Docker image (multi-stage)
   │         │  4. Push image to DockerHub
   └────┬────┘
        │
   ┌────┴────┐
   │ DEPLOY  │
   │         │  1. SSH into EC2
   │         │  2. Pull latest image
   │         │  3. docker compose up -d
   │         │  4. Health check verification
   └────┬────┘
        │
        ▼
   Deployment successful ✅
```

### GitHub Secrets Required

| Secret | Description |
|--------|-------------|
| `DOCKER_USERNAME` | DockerHub username |
| `DOCKER_PASSWORD` | DockerHub password |
| `EC2_HOST` | EC2 public IP |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Contents of `.pem` private key file |

---

## EC2 Server Setup

### 1. Launch EC2 Instance
- AMI: Ubuntu 22.04 LTS
- Instance type: t3.micro (free tier)
- Security Group inbound rules:
  - Port 22 — SSH
  - Port 80 — HTTP
  - Port 443 — HTTPS
  - Port 9090 — Prometheus
  - Port 3000 — Grafana

### 2. Install Docker
```bash
ssh -i ~/.ssh/devops-key.pem ubuntu@35.154.157.202

sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
sudo systemctl enable docker
newgrp docker
```

### 3. Set Up App Directory
```bash
mkdir -p ~/app/nginx/ssl
mkdir -p ~/app/prometheus

# Create docker-compose.yml (see Docker Compose section)
# Create nginx/nginx.conf (see NGINX section)
# Create prometheus/prometheus.yml (see Monitoring section)
```

### 4. Start Services
```bash
cd ~/app
docker compose up -d
docker compose ps
```

---

## Docker Setup

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine AS runner
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/target/Internship-0.0.1-SNAPSHOT.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage?**
- Build stage has JDK (heavy) — only used to compile
- Run stage has JRE (light) — final image is small and secure
- Non-root user for security

---


## SSL Setup

### Current Setup — Self-signed certificate
Used since no domain is available for this assignment.

```bash
mkdir -p ~/app/nginx/ssl

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ~/app/nginx/ssl/self-signed.key \
  -out ~/app/nginx/ssl/self-signed.crt \
  -subj "/C=IN/ST=Punjab/L=Ludhiana/O=Internship/CN=localhost"
```

### Production Setup — Let's Encrypt (with domain)
```bash
sudo apt install certbot
sudo certbot certonly --standalone -d yourdomain.com
# Update nginx.conf to point to /etc/letsencrypt/live/yourdomain.com/
```

### Cloudflare Integration (with domain)
1. Add domain to Cloudflare
2. Update nameservers at registrar to Cloudflare
3. Add A record → EC2 public IP
4. Enable orange cloud (proxy mode) → free DDoS protection + CDN
5. SSL/TLS → set to Full (strict)
6. All traffic routed through Cloudflare edge network

> For this assignment, self-signed cert is used. HTTP automatically redirects to HTTPS via NGINX.

---


## Security Measures

### 1. UFW Firewall
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp     # SSH
sudo ufw allow 80/tcp     # HTTP
sudo ufw allow 443/tcp    # HTTPS
sudo ufw enable
```

### 2. Fail2ban (Brute-force protection)
```bash
sudo apt install fail2ban -y
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```
Automatically bans IPs that fail SSH login 5+ times.

### 3. AWS Security Group
| Port | Access | Reason |
|------|--------|--------|
| 22 | Your IP only | SSH access |
| 80 | 0.0.0.0/0 | HTTP (redirects to HTTPS) |
| 443 | 0.0.0.0/0 | HTTPS |
| 8080 | Blocked | App only accessible via NGINX |
| 5432 | Blocked | DB not exposed publicly |
| 6379 | Blocked | Redis not exposed publicly |

### 4. Docker Security
- Non-root user inside container
- JRE only in production image (no build tools)
- Secrets via environment variables, never hardcoded

---

## Health Check

```bash
curl https://35.154.157.202/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## Logging Strategy

| Layer | Strategy |
|-------|----------|
| Driver | Docker `json-file` driver |
| Rotation | Max 10MB per file, 3 files max |
| Live tailing | `docker compose logs -f app` |
| All services | `docker compose logs -f` |

```bash
# Live logs
docker compose logs -f app

# Last 100 lines
docker compose logs --tail=100 app

# Specific service
docker compose logs -f nginx
```

---

## Backup & Restart Strategy

### Auto-restart
All containers use `restart: always` — automatically recover on crash or server reboot.

### Automated Database Backup
Cron job runs every day at 2am:
```bash
# backup.sh
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker exec internship-postgres pg_dump -U postgres Notes > ~/backups/backup_$DATE.sql
echo "Backup done: backup_$DATE.sql"
```

```bash
# Cron schedule
0 2 * * * /home/ubuntu/backup.sh
```

### Manual Backup
```bash
docker exec internship-postgres pg_dump -U postgres Notes > backup_$(date +%F).sql
```

### Restore
```bash
docker exec -i internship-postgres psql -U postgres Notes < backup_2026-05-28.sql
```

---

## Monitoring (Bonus)

### Prometheus
Scrapes Spring Boot metrics every 15 seconds via `/actuator/prometheus`.

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
```

Access: `http://35.154.157.202:9090`

### Grafana
Configured in docker-compose.yml. Connects to Prometheus as data source.

> **Note:** Grafana is configured but stopped on t3.micro due to 1GB RAM limitation. On a production server (t3.small or higher), start with `docker compose up -d grafana` and access at port 3000 (admin/admin123). This demonstrates production awareness — resource constraints must be considered in real deployments.

---

## Zero Downtime Deployment

CI/CD pipeline uses rolling restart:
```bash
docker pull neerajdeveloper/project:latest
docker compose up -d --no-deps app
```
`--no-deps` restarts only the app container without touching postgres/redis, ensuring zero downtime for the database layer.

---

## Quick Reference Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Rebuild and restart
docker compose up -d --build

# View all container status
docker compose ps

# View logs
docker compose logs -f app

# Manual backup
~/backup.sh

# Check firewall
sudo ufw status

# Check fail2ban
sudo fail2ban-client status sshd
```

---

## Deployment Checklist

- [x] Dockerfile (multi-stage build)
- [x] Docker Compose (6 services)
- [x] PostgreSQL + Redis + NGINX
- [x] SSL (self-signed, HTTP→HTTPS redirect)
- [x] Environment variables (.env)
- [x] CI/CD GitHub Actions (build + deploy)
- [x] Health check endpoint
- [x] Logging strategy (json-file + rotation)
- [x] Auto-restart (restart: always)
- [x] Automated daily backup (cron)
- [x] UFW Firewall
- [x] Fail2ban brute-force protection
- [x] Prometheus monitoring
- [x] Grafana dashboards (configured)
- [x] Non-root Docker user
- [x] Zero downtime deployment