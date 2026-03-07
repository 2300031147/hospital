# Deploying AEROVHYN Backend to Raspberry Pi 4 (Without Git)

This guide shows you how to securely transfer the `backend` folder to your Raspberry Pi and start it up using Docker, without needing `git`.

## Prerequisites
1. **Enable SSH on your Raspberry Pi:** (You can do this via `sudo raspi-config` > Interface Options > SSH).
2. **Install Docker & Docker-Compose on your Pi:**
```bash
# Run this *on the Pi* if you haven't installed Docker yet
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker scout # (Log out and log back in on the Pi for this to take effect)
sudo apt-get install docker-compose
```

---

## Step 1: Transfer Files Using SCP (Secure Copy Protocol)

Since we just added `docker-compose.yml` to the root `d:\Hospital` directory, we need to copy both the `backend` folder and the `docker-compose.yml` file to the Pi.

Open PowerShell or Command Prompt on your **Windows machine** and run:

```bash
# 1. Copy the entire 'backend' folder via Tailscale IP
scp -r "d:\Hospital\backend" scout@100.104.158.108:~/aerovhyn_backend

# 2. Copy the 'docker-compose.yml' file into that same folder on the Pi
scp "d:\Hospital\docker-compose.yml" scout@100.104.158.108:~/aerovhyn_backend/docker-compose.yml
```

*(Note: Ensure your device is logged into Tailscale to communicate with `100.104.158.108`).*

---

## Step 2: Start the Backend on the Pi

Now, SSH into your Raspberry Pi from your Windows machine:
```bash
ssh scout@100.104.158.108
```

Once inside the Pi's terminal, navigate to the folder where you uploaded the files and start Docker:

```bash
cd ~/aerovhyn_backend

# Build the image uniquely mapped to the Pi's ARM64 architecture, and start it in the background (-d)
docker-compose up --build -d
```

### Useful Management Commands (Run on the Pi)

- **View Live Logs:**
  ```bash
  docker-compose logs -f backend
  ```
- **Stop the Server:**
  ```bash
  docker-compose down
  ```
- **Check if it's running:**
  ```bash
  docker ps
  ```

Your backend will now be alive and accessible on your Tailscale network at `http://100.104.158.108:8000`!
