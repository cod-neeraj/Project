# 🚀 DevOps Assignment — Spring Boot Production Deployment

## Architecture Overview

```
Internet
   │
   ▼
[NGINX :80/:443]  ← Reverse proxy + SSL termination
   │
   ▼
[Spring Boot :8080]  ← Application
   ├── [PostgreSQL :5432]  ← Primary database
   └── [Redis :6379]       ← Cache / session store
```

All services run on a single AWS EC2 t2.micro instance via Docker Compose.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Application | Spring Boot 3.x |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Reverse Proxy | NGINX |
| Container Runtime | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Cloud | AWS EC2 (Ubuntu 22.04) |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose installed
- AWS EC2 instance running
- GitHub repository with secrets configured

### 1. Clone & Configure
```bash
git clone https://github.com/cod-neeraj/Project
cd Project
cp .env.example .env
# Edit .env with your values
```

### 2. Run Locally
```bash
docker compose up -d
```

### 3. Access
- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health

---

## CI/CD Pipeline

### How it works
```
Push to main branch
       │
       ▼
GitHub Actions triggered
       │
  ┌────┴────┐
  │  BUILD  │  → Maven build → Docker image → Push to GHCR
  └────┬────┘
       │
  ┌────┴────┐
  │ DEPLOY  │  → SSH to EC2 → Pull image → docker compose up
  └────┬────┘
       │
       ▼
  Health check verified ✅
```

### GitHub Secrets Required
Go to: GitHub Repo → Settings → Secrets → Actions

| Secret | Value |
|--------|-------|
| `EC2_HOST` | `35.154.157.202` |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Contents of your `devops-key.pem` file |

---

## EC2 Server Setup

### Initial Setup
```bash
# SSH into server
ssh -i ~/.ssh/devops-key.pem ubuntu@35.154.157.202

# Install Docker
sudo apt update
sudo apt install -y docker.io docker-compose git
sudo usermod -aG docker ubuntu
sudo systemctl enable docker

# Clone repo
mkdir -p /home/ubuntu/app
cd /home/ubuntu/app
git clone https://github.com/cod-neeraj/Project .

# Start services
docker compose up -d
```

### Check status
```bash
docker compose ps
docker compose logs -f app
```

---

## SSL Setup

### Option A — No domain (Self-signed cert)
```bash
mkdir -p nginx/ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx/ssl/key.pem \
  -out nginx/ssl/cert.pem \
  -subj "/CN=35.154.157.202"
```

### Option B — With domain (Let's Encrypt)
```bash
sudo apt install certbot
sudo certbot certonly --standalone -d yourdomain.com
# Copy certs to nginx/ssl/
```

> **Note:** For this assignment, self-signed cert is used since no domain is available. In production, Let's Encrypt or AWS ACM would be used.

---

## Environment Variables

```env
# Application
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/Notes
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<strong-password>

# Redis
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# PostgreSQL
POSTGRES_DB=Notes
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<strong-password>
```

> ⚠️ Never commit `.env` to Git. Use GitHub Secrets for CI/CD.

---

## Security Measures

### Firewall (UFW)
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
```

### Fail2ban (Brute-force protection)
```bash
sudo apt install fail2ban
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

### AWS Security Group
- Port 22 (SSH) — your IP only
- Port 80 (HTTP) — 0.0.0.0/0
- Port 443 (HTTPS) — 0.0.0.0/0
- Port 8080 — blocked from public (only NGINX talks to it internally)
- Port 5432/6379 — blocked from public

---

## Health Check

Spring Boot Actuator exposes:
```
GET /actuator/health  → {"status":"UP"}
GET /actuator/info    → app info
GET /actuator/metrics → JVM metrics
```

---

## Logging Strategy

| Layer | Strategy |
|-------|----------|
| Application | JSON logs via Docker `json-file` driver |
| Log rotation | Max 10MB per file, 3 files retained |
| Centralized | `docker compose logs -f` for live tailing |
| Monitoring | (Bonus) Prometheus + Grafana |

```bash
# View live logs
docker compose logs -f app

# View last 100 lines
docker compose logs --tail=100 app
```

---

## Backup & Restart Strategy

### Auto-restart
All containers have `restart: always` — auto-recover on crash or server reboot.

### Database Backup
```bash
# Manual backup
docker exec internship-postgres pg_dump -U postgres Notes > backup_$(date +%F).sql

# Automated daily backup (cron)
crontab -e
# Add: 0 2 * * * docker exec internship-postgres pg_dump -U postgres Notes > /backups/backup_$(date +\%F).sql
```

### Restore
```bash
docker exec -i internship-postgres psql -U postgres Notes < backup_2024-01-01.sql
```

---

## Monitoring (Bonus)

Prometheus + Grafana stack available at:
- Grafana: http://35.154.157.202:3000
- Metrics: Spring Actuator → Prometheus → Grafana

---

## Deployment Instructions

1. Push code to `main` branch
2. GitHub Actions automatically:
   - Builds the Spring Boot JAR
   - Creates Docker image
   - Pushes to GitHub Container Registry
   - SSHs into EC2
   - Pulls new image
   - Restarts containers with zero downtime
3. Health check confirms deployment success

**Live URL:** `http://35.154.157.202/actuator/health`
