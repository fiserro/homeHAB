# homeHAB Infrastructure Installation Guide

Complete guide for setting up a home automation system on Raspberry Pi with OpenHAB, Zigbee2MQTT, and MQTT broker.

## Architecture Overview

### Production Architecture (Raspberry Pi)

All services run on a single Raspberry Pi in production:

```
                           ┌──────────────────┐
                           │  Web Browser     │
                           │  (Client)        │
                           └────────┬─────────┘
                                    │ HTTP :80
                                    ▼
┌───────────────────────────────────────────────────────────────┐
│                 Raspberry Pi (openhab.home)                   │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    Nginx Reverse Proxy                   │  │
│  │         openhab.home → :8080                            │  │
│  │         zigbee.home  → :8081                            │  │
│  └────────────────┬────────────────────┬───────────────────┘  │
│                   │                    │                      │
│          ┌────────▼────────┐  ┌────────▼────────┐            │
│          │    OpenHAB      │  │  Zigbee2MQTT    │            │
│          │    :8080        │  │  :8081          │            │
│          └────────┬────────┘  └────────┬────────┘            │
│                   │                    │                      │
│                   └────────┬───────────┘                      │
│                            │ MQTT                             │
│                   ┌────────▼────────┐                         │
│                   │   Mosquitto     │                         │
│                   │   :1883         │                         │
│                   └─────────────────┘                         │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  HRV Bridge  │  │   InfluxDB   │  │       Grafana        │ │
│  │  (Python)    │  │   :8086      │  │       :3000          │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Zigbee USB Dongle (/dev/ttyUSB0)           │  │
│  └────────────────────────────┬────────────────────────────┘  │
└───────────────────────────────┼───────────────────────────────┘
                                │ Zigbee / PWM
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
     ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
     │ Zigbee Devices │ │  ESP32 Panel   │ │  PWM Modules   │
     │ (sensors, etc.)│ │  (HRV control) │ │  (GPIO 18, 19) │
     └────────────────┘ └────────────────┘ └────────────────┘
```

### Development Architecture (Current Setup)

Development uses a hybrid approach: OpenHAB runs locally in Docker on Mac,
while other services (MQTT, Zigbee, sensors) are shared from the production RPi.
This allows testing with real sensors without duplicating hardware.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Mac (Development)                               │
│  ┌─────────────────────┐                                                    │
│  │   Docker            │                                                    │
│  │   ┌──────────────┐  │     MQTT (zigbee.home:1883)                       │
│  │   │   OpenHAB    │──┼─────────────────────┐                              │
│  │   │   :8888      │  │                     │  topic: homehab/*       │
│  │   └──────────────┘  │                     │                              │
│  └─────────────────────┘                     │                              │
└──────────────────────────────────────────────┼──────────────────────────────┘
                                               │
                                               ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                          Raspberry Pi (Production)                           │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │   OpenHAB    │  │  Zigbee2MQTT │  │   Mosquitto  │  │   HRV Bridge     │ │
│  │   :8080      │  │   :8081      │  │   :1883      │  │   (Python)       │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────────┘ │
│         │                                  │                                 │
│         │ topic: homehab/*                 │ topic: homehab/*           │
│         └──────────────────────────────────┘                                 │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐                                         │
│  │   InfluxDB   │  │   Grafana    │  ← Uses tags to separate dev/prod data │
│  │   :8086      │  │   :3000      │                                         │
│  └──────────────┘  └──────────────┘                                         │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Key points:**
- Both Dev and Prod OpenHAB use the same MQTT topic prefix (`homehab/`)
- Single HRV Bridge on RPi serves both environments
- Real sensors and HRV control are accessible in dev environment

**Future option:** If needed, environments can be separated using different prefixes.

### Future: Fully Separated Environments (2 RPi)

If you add a second Raspberry Pi for development, you can fully separate environments:

```
┌────────────────────────────────┐     ┌────────────────────────────────┐
│     Dev RPi (dev-rpi.home)     │     │    Prod RPi (openhab.home)     │
│                                │     │                                │
│  OpenHAB, Mosquitto, Z2M,      │     │  OpenHAB, Mosquitto, Z2M,      │
│  HRV Bridge, InfluxDB, Grafana │     │  HRV Bridge, InfluxDB, Grafana │
│                                │     │                                │
│  topic: homehab/*          │     │  topic: homehab/*              │
└────────────────────────────────┘     └────────────────────────────────┘
```

**To switch `.env.dev` from local Docker to remote dev RPi:**

```bash
# Change deployment type
DEPLOY_TYPE=remote
DEPLOY_TARGET=robertfiser@dev-rpi.home:/etc/openhab

# Enable Python deployment to dev RPi
PYTHON_DEPLOY_ENABLED=true
PYTHON_DEPLOY_HOST=robertfiser@dev-rpi.home
```

No changes needed in `.env.prod` - it already deploys to the production RPi.

## Prerequisites

### Hardware

| Component | Description |
|-----------|-------------|
| Raspberry Pi 4/5 | Main controller |
| NVMe SSD + USB enclosure | Boot drive (faster than SD card) |
| Zigbee USB dongle | CC2652, SONOFF Zigbee 3.0, or similar |
| EdgeRouter (optional) | For custom DNS domain names |

### Software (on your computer)

- Raspberry Pi Imager
- SSH client
- Python 3 (for temporary HTTP server)

---

## Part 1: Prepare Installation Media

### 1.1 Publish SSH Key via HTTP

Before flashing, prepare your SSH public key for automatic download during first boot.

On your Mac/Linux:

```bash
cd ~/.ssh
mkdir -p public
cp id_rsa.pub public/
cd public
python3 -m http.server 8000
```

Note the URL: `http://<your-computer-ip>:8000/id_rsa.pub`

Keep the server running until RPi completes first boot.

### 1.2 Flash OpenHABian to NVMe

1. Connect NVMe SSD via USB enclosure to your computer
2. Open **Raspberry Pi Imager**
3. Choose OS: **Other specific-purpose OS → Home automation → OpenHABian**
4. Choose Storage: Your NVMe drive
5. Click gear icon for **Advanced Settings**:

| Setting | Value |
|---------|-------|
| Hostname | `openhab` |
| Username | your username |
| Password | your password |
| SSH | Enable, paste URL from step 1.1 |
| WiFi SSID | your network name |
| WiFi Password | your network password |
| Locale | your keyboard layout |

6. Click **Write** and wait for completion

---

## Part 2: First Boot

### 2.1 Boot Raspberry Pi

1. Disconnect NVMe from computer
2. Connect NVMe to Raspberry Pi USB 3.0 port
3. Power on Raspberry Pi
4. Wait 5-10 minutes for initial setup (OpenHABian installs packages)

### 2.2 Verify SSH Access

```bash
ssh <username>@openhab.local
```

If `openhab.local` doesn't resolve, use the IP address from your router's DHCP client list.

### 2.3 Verify NVMe Boot

```bash
df -h | grep "/$"
```

Expected: `/dev/nvme0n1p2` or `/dev/sda2` (USB-attached NVMe)

---

## Part 3: Install Core Services

Run all commands on Raspberry Pi via SSH.

### 3.1 Update System

```bash
sudo apt-get update && sudo apt-get upgrade -y
```

### 3.2 Install Mosquitto MQTT Broker

```bash
sudo apt-get install -y mosquitto mosquitto-clients
sudo systemctl enable --now mosquitto
```

**Test MQTT:**

Terminal 1:
```bash
mosquitto_sub -t test/topic -v
```

Terminal 2:
```bash
mosquitto_pub -t test/topic -m "hello"
```

You should see "hello" appear in Terminal 1.

### 3.3 Install Node.js 22

Required for Zigbee2MQTT.

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
node --version   # Verify: v22.x.x
```

### 3.4 Install Zigbee2MQTT

**Detect Zigbee dongle:**

```bash
ls -l /dev/ttyUSB*
```

Note the path (e.g., `/dev/ttyUSB0`).

**Install:**

```bash
sudo mkdir -p /opt/zigbee2mqtt
sudo chown -R $USER:$USER /opt/zigbee2mqtt
cd /opt/zigbee2mqtt
git clone --depth 1 https://github.com/Koenkk/zigbee2mqtt.git .
npm ci
```

**Configure** `/opt/zigbee2mqtt/data/configuration.yaml`:

```yaml
homeassistant: false
permit_join: false
frontend:
  port: 8081
mqtt:
  base_topic: zigbee2mqtt
  server: mqtt://localhost
serial:
  port: /dev/ttyUSB0   # <-- your dongle path
advanced:
  network_key: GENERATE
  pan_id: GENERATE
```

**Test run:**

```bash
npm start
```

Verify no errors, then Ctrl+C to stop.

**Create systemd service** `/etc/systemd/system/zigbee2mqtt.service`:

```bash
sudo tee /etc/systemd/system/zigbee2mqtt.service << 'EOF'
[Unit]
Description=Zigbee2MQTT
After=network.target mosquitto.service

[Service]
ExecStart=/usr/bin/npm start
WorkingDirectory=/opt/zigbee2mqtt
Restart=always
User=YOUR_USERNAME
Environment="NODE_ENV=production"

[Install]
WantedBy=multi-user.target
EOF
```

Replace `YOUR_USERNAME` with your actual username.

**Enable and start:**

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now zigbee2mqtt
sudo systemctl status zigbee2mqtt
```

---

## Part 4: Configure Nginx Reverse Proxy

Allows accessing services via friendly URLs on port 80.

### 4.1 Install Nginx

```bash
sudo apt-get install -y nginx
```

### 4.2 Create Site Configuration

Create a single configuration file for all services:

```bash
sudo nano /etc/nginx/sites-available/homehab
```

Paste the following content:

```nginx
# OpenHAB
server {
    listen 80;
    server_name openhab.home;

    location / {
        proxy_pass http://127.0.0.1:8080/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

# Zigbee2MQTT
server {
    listen 80;
    server_name zigbee.home;

    location / {
        proxy_pass http://127.0.0.1:8081/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

# Grafana
server {
    listen 80;
    server_name grafana.home;

    location / {
        proxy_pass http://127.0.0.1:3000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Save with `Ctrl+O`, Enter, `Ctrl+X`.

### 4.3 Enable Site

```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/homehab /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## Part 5: Configure DNS (EdgeRouter)

Optional: Create friendly domain names `openhab.home`, `zigbee.home`, and `grafana.home`.

Skip this section if you're fine with using IP addresses or `rpi.local` (mDNS).

### 5.1 Find Raspberry Pi IP

```bash
hostname -I
```

Note the IP address (e.g., `192.168.1.132`).

### 5.2 Configure EdgeRouter

```bash
ssh ubnt@192.168.1.1
configure

# Set DNS records (replace IP with your RPi's IP)
set service dns forwarding options "host-record=openhab.home,192.168.1.132"
set service dns forwarding options "host-record=zigbee.home,192.168.1.132"
set service dns forwarding options "host-record=grafana.home,192.168.1.132"
set service dns forwarding listen-on switch0

commit
save
exit
```

### 5.3 Verify DNS

From any client on the network:

```bash
nslookup openhab.home
```

Expected:
```
Name:    openhab.home
Address: 192.168.1.132
```

---

## Part 6: Configure OpenHAB

### 6.1 Access OpenHAB

Open in browser:
- `http://openhab.home` (with DNS configured)
- `http://openhab.local:8080` (mDNS)
- `http://<ip-address>:8080` (direct IP)

### 6.2 Install MQTT Binding

1. Go to **Settings → Add-ons → Bindings**
2. Search for **MQTT Binding**
3. Click **Install**

### 6.3 Create MQTT Broker Thing

1. Go to **Settings → Things → Add (+)**
2. Select **MQTT Binding**
3. Select **MQTT Broker**
4. Configure:
   - Broker Hostname: `localhost`
   - Broker Port: `1883`
5. Save

---

## Part 7: Pair Zigbee Devices

1. Open Zigbee2MQTT frontend:
   - `http://zigbee.home` (with DNS)
   - `http://openhab.local:8081` (mDNS)
2. Click **Permit join** (top right)
3. Put your Zigbee device in pairing mode
4. Device appears in the list when paired
5. **Disable Permit join** when done

---

## Service Management

### Commands

| Action | Command |
|--------|---------|
| Start | `sudo systemctl start <service>` |
| Stop | `sudo systemctl stop <service>` |
| Restart | `sudo systemctl restart <service>` |
| Status | `sudo systemctl status <service>` |
| Logs | `sudo journalctl -u <service> -f` |

Services: `openhab`, `zigbee2mqtt`, `mosquitto`, `nginx`

### Quick Reference

```bash
# Restart all services
sudo systemctl restart mosquitto zigbee2mqtt openhab nginx

# View Zigbee2MQTT logs
sudo journalctl -u zigbee2mqtt -f

# View OpenHAB logs
sudo journalctl -u openhab -f
```

---

## Troubleshooting

### Cannot SSH to Raspberry Pi

1. Check if RPi is powered and connected to network
2. Try `ping openhab.local`
3. Find IP in router's DHCP client list
4. Verify WiFi credentials were correct during imaging

### DNS Names Not Working

1. Verify router DNS configuration
2. Flush client DNS cache:
   - macOS: `sudo dscacheutil -flushcache`
   - Windows: `ipconfig /flushdns`
3. Try `openhab.local` (mDNS) instead

### Zigbee Dongle Not Detected

1. Check USB connection
2. Try different USB port
3. Check permissions:
   ```bash
   ls -l /dev/ttyUSB*
   sudo usermod -a -G dialout $USER
   # Log out and back in
   ```

### Zigbee2MQTT Won't Start

1. Check logs: `sudo journalctl -u zigbee2mqtt -f`
2. Verify Mosquitto is running: `sudo systemctl status mosquitto`
3. Check serial port path in configuration
4. Verify dongle permissions (see above)

### OpenHAB Can't Connect to MQTT

1. Verify Mosquitto: `sudo systemctl status mosquitto`
2. Test MQTT locally:
   ```bash
   mosquitto_pub -t test -m "hello"
   mosquitto_sub -t test -v
   ```
3. Check OpenHAB Thing status in UI

---

## Ports Reference

| Service        | Port | URL                    |
|----------------|------|------------------------|
| OpenHAB        | 8080 | `http://openhab.home`  |
| Zigbee2MQTT    | 8081 | `http://zigbee.home`   |
| Grafana        | 3000 | `http://grafana.home`  |
| Mosquitto MQTT | 1883 | (internal)             |
| Nginx          | 80   | (reverse proxy)        |

---

## References

- [OpenHABian Documentation](https://www.openhab.org/docs/installation/openhabian.html)
- [Zigbee2MQTT Documentation](https://www.zigbee2mqtt.io/)
- [Mosquitto Documentation](https://mosquitto.org/documentation/)
- [EdgeRouter DNS Configuration](https://help.ui.com/hc/en-us/articles/115010913367)
