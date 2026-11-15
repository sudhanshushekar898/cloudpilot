# Deploying CloudPilot to AWS EC2

One small VM runs the whole production stack from `deploy/docker-compose.prod.yml`:
PostgreSQL, the backend, the web app, Caddy (HTTPS), Prometheus and Grafana. Images come from
GHCR, published by the release workflow for every green commit on `main`.

```
Internet ──443──▶ Caddy ──▶ frontend (nginx) ──/api──▶ backend ──▶ PostgreSQL
                    └──/grafana──▶ Grafana ◀── Prometheus ◀── backend :8081 (private)
```

Expected cost: a `t3.small` (2 vCPU, 2 GB) is about US$15/month on demand, plus about US$1 for
the 20 GB disk. The free-tier `t3.micro` (1 GB) is too small for the JVM plus PostgreSQL.

## 1. AWS account safety first

1. Turn on MFA for the root user, then create an IAM admin user for daily work.
2. **Billing → Budgets → Create budget → Monthly cost budget**, e.g. US$20, with an email alert at
   80%. Do this before launching anything.

## 2. Launch the instance

EC2 → Launch instance:

| Setting | Value |
|---|---|
| AMI | Ubuntu Server 24.04 LTS (x86_64) |
| Type | `t3.small` |
| Key pair | create an **ed25519** key pair, download the `.pem` |
| Storage | 20 GB gp3 |
| Security group | SSH 22 from **My IP** only; HTTP 80 and HTTPS 443 from anywhere |

Then **Elastic IPs → Allocate → Associate** with the instance, so the address survives restarts.
Your public host name is `<elastic-ip>.sslip.io`, for example `203.0.113.10.sslip.io`. sslip.io
resolves it to the IP, so Caddy can get a Let's Encrypt certificate without buying a domain.

## 3. Prepare the server

```bash
ssh -i cloudpilot.pem ubuntu@<elastic-ip>
```

```bash
# Docker Engine + Compose plugin
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && newgrp docker

# 2 GB swap: a safety net for memory spikes on a 2 GB machine
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# The repository holds the Compose, Caddy, Prometheus and Grafana config
git clone https://github.com/sudhanshushekar898/cloudpilot.git ~/cloudpilot
cd ~/cloudpilot/deploy && cp .env.prod.example .env && chmod 600 .env
```

Edit `deploy/.env`: set `DOMAIN`, and generate each secret with `openssl rand -base64 48`
(`DEMO_PASSWORD` is the password you publish in the README for the demo accounts; it must not be
the dev default). Leave `IMAGE_TAG` empty; the deploy script fills it in.

## 4. Make the images pullable

GitHub → your profile → **Packages** → `cloudpilot-backend` → Package settings → Change
visibility → **Public**. Repeat for `cloudpilot-frontend`. (Alternative for private packages:
`docker login ghcr.io` on the server with a token that only has `read:packages`.)

## 5. First deploy by hand

Pick the latest green commit on `main` (the release workflow has published its images):

```bash
cd ~/cloudpilot && git fetch && deploy/deploy.sh <full-commit-sha>
```

Open `https://<elastic-ip>.sslip.io`. The first request can take a few seconds while Caddy
obtains the certificate.

## 6. Deploys from GitHub Actions

Create an SSH key just for deploys, so the workflow never holds your personal key:

```bash
ssh-keygen -t ed25519 -f cloudpilot-deploy -N ""                # on your machine
ssh-copy-id -i cloudpilot-deploy.pub -o "IdentityFile=cloudpilot.pem" ubuntu@<elastic-ip>
```

Repository → Settings → **Environments → New environment** `production` (optionally add yourself
as a required reviewer), then add these environment secrets:

| Secret | Value |
|---|---|
| `EC2_HOST` | the Elastic IP |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | contents of the private key file `cloudpilot-deploy` |
| `APP_DOMAIN` | `<elastic-ip>.sslip.io` |

Actions → **Deploy** → Run workflow → paste a commit SHA. The job deploys over SSH, waits for the
backend health check, then smoke-tests the public URL.

## Rollback

Images are tagged with the commit SHA and never overwritten, so every release stays runnable.

| Situation | What to do |
|---|---|
| New backend never becomes healthy | Nothing: `deploy.sh` rolls back to the previous release by itself and the job fails. |
| Healthy but wrong (a bug) | Run the Deploy workflow with `rollback`, or with any older SHA. |
| Claude misbehaves or costs spike | No deploy needed: an admin sets `PUT /api/v1/features/claude-ai {"enabled": false}`. Analysis and postmortems switch to the rule-based and template paths at once. |

Database migrations are forward-only and additive (new tables and columns, nothing renamed or
dropped in the same release), so the previous backend still runs against the newer schema. A
destructive change would be split across two releases: stop using the column first, drop it later.

## Operating it

```bash
cd ~/cloudpilot/deploy
docker compose -p cloudpilot -f docker-compose.prod.yml --env-file .env ps        # status
docker compose -p cloudpilot -f docker-compose.prod.yml --env-file .env logs -f backend
cat .releases                                                                      # release history
```

Backups: `docker compose ... exec db pg_dump -U cloudpilot cloudpilot | gzip > backup.sql.gz`,
copied off the machine. For a portfolio demo, an EBS snapshot schedule (Data Lifecycle Manager)
is the low-effort option.

Stopping costs: **stop** the instance when you don't need it (you pay only for the disk and the
idle Elastic IP), and **terminate** it plus release the Elastic IP when you're done.
