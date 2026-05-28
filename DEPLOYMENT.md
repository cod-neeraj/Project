# Deployment Guide

Complete step-by-step instructions to deploy this project from scratch.

---

## Prerequisites
- AWS account (free tier)
- GitHub account
- DockerHub account
- Local machine with Git installed

---

## Step 1 — Launch EC2 Instance

1. AWS Console → EC2 → Launch Instance
2. AMI: **Ubuntu 22.04 LTS**
3. Instance type: **t3.micro** (free tier)
4. Key pair: Create new → download `.pem` file → save safely
5. Security Group inbound rules:

| Port | Source | Purpose |
|------|--------|---------|
| 22 | Your IP | SSH |
| 80 | 0.0.0.0/0 | HTTP |
| 443 | 0.0.0.0/0 | HTTPS |
| 9090 | 0.0.0.0/0 | Prometheus |
| 3000 | 0.0.0.0/0 | Grafana |

6. Launch instance

---

## Step 2 — Connect to EC2

```bash
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@YOUR_EC2_IP
```

---

## Step 3 — Install Docker

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
sudo systemctl enable docker
sudo systemctl start docker
newgrp docker
```

---

## Step 4 — Set Up Server

```bash
mkdir -p ~/app/nginx/ssl
mkdir -p ~/app/prometheus
mkdir -p ~/backups

# Generate SSL certificate
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ~/app/nginx/ssl/self-signed.key \
  -out ~/app/nginx/ssl/self-signed.crt \
  -subj "/C=IN/ST=Punjab/O=Internship/CN=localhost"
```

---

## Step 5 — Create Config Files on Server

### nginx.conf
```bash
cat > ~/app/nginx/nginx.conf << 'NGINX'
events { worker_connections 1024; }
http {
    upstream app { server app:8080; }
    server {
        listen 80;
        return 301 https://$host$request_uri;
    }
    server {
        listen 443 ssl;
        ssl_certificate /etc/nginx/ssl/self-signed.crt;
        ssl_certificate_key /etc/nginx/ssl/self-signed.key;
        location / {
            proxy_pass http://app;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        location /actuator/health { proxy_pass http://app/actuator/health; }
    }
}
NGINX
```

### prometheus.yml
```bash
cat > ~/app/prometheus/prometheus.yml << 'PROM'
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
PROM
```

### docker-compose.yml
Copy the docker-compose.yml from the repo to `~/app/docker-compose.yml` with your DockerHub image name.

---

## Step 6 — Security Setup

```bash
# Firewall
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 9090/tcp
sudo ufw allow 3000/tcp
sudo ufw enable

# Fail2ban
sudo apt install fail2ban -y
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

---

## Step 7 — Automated Backup

```bash
cat > ~/backup.sh << 'BACKUP'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker exec internship-postgres pg_dump -U postgres Notes > ~/backups/backup_$DATE.sql
ls -t ~/backups/backup_*.sql | tail -n +8 | xargs -r rm
echo "Backup done: backup_$DATE.sql"
BACKUP

chmod +x ~/backup.sh

# Add cron - runs every day at 2am
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/backup.sh") | crontab -
```

---

## Step 8 — GitHub Actions Setup

Add these secrets to: GitHub Repo → Settings → Secrets → Actions

| Secret | Value |
|--------|-------|
| `DOCKER_USERNAME` | Your DockerHub username |
| `DOCKER_PASSWORD` | Your DockerHub password |
| `EC2_HOST` | EC2 public IP address |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Full contents of your `.pem` file |

---

## Step 9 — Deploy

Push to main branch — GitHub Actions will:
1. Build Docker image
2. Push to DockerHub
3. SSH into EC2
4. Pull and restart app container
5. Verify health check

```bash
git push origin main
```

Watch it run: GitHub → Actions tab → CI/CD Pipeline

---

## Step 10 — Verify

```bash
# Health check
curl -k https://YOUR_EC2_IP/actuator/health

# All containers running
docker compose ps

# Prometheus
curl http://YOUR_EC2_IP:9090/-/healthy
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| SSH not connecting | Check Security Group port 22 open |
| App not starting | `docker compose logs app` |
| DB connection error | `docker compose down -v && docker compose up -d` |
| NGINX not starting | Check `~/app/nginx/nginx.conf` exists |
| Out of memory | `docker compose stop grafana` |
| CI/CD failing | Check GitHub Secrets are set correctly |
