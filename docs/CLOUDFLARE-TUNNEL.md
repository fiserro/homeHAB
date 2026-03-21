# Cloudflare Tunnel Setup

Remote access to homeHAB services via Cloudflare Tunnel with Zero Trust (Google OAuth + One-time PIN).

## Architecture

```
Internet → Cloudflare Edge (SSL + Zero Trust Auth) → Tunnel → RPi localhost
```

No ports are opened on EdgeRouter X. The tunnel is outbound-only from RPi.
No public IP address is required — the tunnel works even behind CG-NAT.

## URLs

| Public URL | Service | RPi Port |
|---|---|---|
| `https://fiserhub.fun` | Landing page | 80 (Nginx) |
| `https://openhab.fiserhub.fun` | OpenHAB | 8080 |
| `https://zigbee.fiserhub.fun` | Zigbee2MQTT | 8081 |
| `https://grafana.fiserhub.fun` | Grafana | 3000 |

MQTT (1883) and InfluxDB (8086) are NOT exposed.

## Current Configuration

- **Domain**: `fiserhub.fun` (registered at Wedos — https://client.wedos.com/)
- **Cloudflare nameservers**: `armfazh.ns.cloudflare.com`, `clarissa.ns.cloudflare.com`
- **Tunnel ID**: `74faf9e3-40ad-49e8-a7c5-409f102bcb7c`
- **Tunnel name**: `homehab`
- **Zero Trust team name**: `fiserhub`
- **Zero Trust dashboard**: https://one.dash.cloudflare.com/
- **Google OAuth project**: "FiserHub Auth" in Google Cloud Console
- **Redirect URI**: `https://fiserhub.cloudflareaccess.com/cdn-cgi/access/callback`
- **cloudflared runs as**: Docker container (`homehab-cloudflared`) with `--network host`
- **Credentials stored at**: `~/.cloudflared/` on RPi (mounted into container)
- **SSL mode**: Full (Cloudflare → tunnel encrypted, tunnel → localhost HTTP)

## Authentication

All services are protected by Cloudflare Zero Trust Access. Two login methods are available:

1. **Google OAuth** — for users with Google accounts. Redirects to Google login.
2. **One-time PIN** — for users without Google accounts. Cloudflare sends a PIN to their email.

### Managing Users

Users are managed via Access policies in Cloudflare Zero Trust:

1. Go to **Zero Trust** → **Access** → **Applications** → click application → **Policies**
2. Edit the Allow policy → add/remove email addresses in **Include** rules
3. Save

To revoke an active session immediately:
**Zero Trust** → **Access** → **Logs/Active Sessions** → find session → **Revoke**

Session duration is 24 hours — after expiry, users must re-authenticate.

## Prerequisites

- Domain `fiserhub.fun` (registered at Wedos)
- Cloudflare account (free plan)
- Docker installed on RPi
- SSH access to RPi (`robertfiser@openhab.home`)

## Setup

### 1. Cloudflare + Domain

1. Create account at https://dash.cloudflare.com/sign-up
2. Add site `fiserhub.fun` (free plan)
3. Assigned nameservers:
   - `armfazh.ns.cloudflare.com`
   - `clarissa.ns.cloudflare.com`
4. Log in to Wedos (https://client.wedos.com/) → domain management → **DNS servers** → **Set Own DNS Servers** → enter Cloudflare NS
5. Wait for DNS propagation (typically 15-60 minutes)
6. Verify domain shows "Active" in Cloudflare dashboard

### 2. Install Docker on RPi

```bash
ssh robertfiser@openhab.home
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker robertfiser
# Log out and back in for group to take effect
```

### 3. Authenticate and Create Tunnel

```bash
# Fix permissions for cloudflared container
mkdir -p ~/.cloudflared
sudo chmod 777 ~/.cloudflared

# Authenticate (opens browser URL - copy to Mac browser, select fiserhub.fun)
docker run -it --rm \
  -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel login

# Create tunnel
docker run -it --rm \
  -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel create homehab
# Output: Created tunnel homehab with id 74faf9e3-40ad-49e8-a7c5-409f102bcb7c
```

### 4. Register DNS Routes

```bash
docker run --rm -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel route dns homehab fiserhub.fun

docker run --rm -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel route dns homehab openhab.fiserhub.fun

docker run --rm -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel route dns homehab zigbee.fiserhub.fun

docker run --rm -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel route dns homehab grafana.fiserhub.fun
```

> **Note:** If a DNS record already exists (e.g., A record), delete it first in Cloudflare dashboard → DNS → Records.

### 5. Configure and Run Tunnel

The tunnel config is at `cloudflare/config.yml` in this repo. Run the container:

```bash
docker run -d \
  --name homehab-cloudflared \
  --restart unless-stopped \
  --network host \
  -v /etc/cloudflared/config.yml:/etc/cloudflared/config.yml:ro \
  -v ~/.cloudflared:/home/nonroot/.cloudflared:ro \
  cloudflare/cloudflared:latest tunnel --config /etc/cloudflared/config.yml run
```

Or use docker-compose (production profile):

```bash
docker compose --profile prod up -d cloudflared
```

### 6. Update Landing Page

Deploy the updated landing page that detects LAN vs internet access:

```bash
sudo cp cloudflare/nginx-landing.conf /etc/nginx/sites-available/rpi
sudo nginx -t
sudo systemctl reload nginx
```

When accessed from LAN (`.home` hostname), links point to `http://*.home`.
When accessed from internet (`fiserhub.fun`), links point to `https://*.fiserhub.fun`.

### 7. Cloudflare Zero Trust Access

#### 7.1 Google OAuth Credentials

1. Go to https://console.cloud.google.com/apis/credentials
2. Create project "FiserHub Auth" (or use existing)
3. Set up OAuth consent screen (Get started → app name, email, External audience)
4. Create OAuth client ID:
   - Type: Web application
   - Name: "Cloudflare Access"
   - Authorized redirect URI: `https://fiserhub.cloudflareaccess.com/cdn-cgi/access/callback`
5. Note Client ID and Client Secret

#### 7.2 Configure Cloudflare Zero Trust

1. Go to https://one.dash.cloudflare.com/
2. Choose team name: `fiserhub` — free plan (requires payment method, nothing is charged)
3. Settings → Authentication → Login methods → Add **Google** with Client ID + Secret
4. One-time PIN is enabled by default (for users without Google accounts)

#### 7.3 Create Access Application

1. Access → Applications → Add application → **Self-hosted**
2. Application name: "FiserHub"
3. Domain: `*.fiserhub.fun` (protects all subdomains)
4. Add additional hostname: `fiserhub.fun` (protects landing page)
5. Session duration: 24 hours

#### 7.4 Create Allow Policy

- Policy name: "Allowed users"
- Action: Allow
- Include → Selector: **Emails** → add allowed email addresses
- Both Google and non-Google emails can be added (non-Google users authenticate via One-time PIN)

## Management

```bash
# Check tunnel status
docker logs homehab-cloudflared

# Restart tunnel
docker restart homehab-cloudflared

# Stop tunnel
docker stop homehab-cloudflared

# Start tunnel
docker start homehab-cloudflared

# List tunnels (from RPi)
docker run --rm -v ~/.cloudflared:/home/nonroot/.cloudflared \
  cloudflare/cloudflared:latest tunnel list
```

### Cloudflare Dashboard

- **DNS Records**: Cloudflare dashboard → DNS → Records
- **Tunnel status**: Cloudflare dashboard → Zero Trust → Networks → Tunnels
- **Access logs**: Zero Trust → Access → Logs
- **User management**: Zero Trust → Access → Applications → Policies
- **Session revocation**: Zero Trust → Access → Logs → Revoke session

## Troubleshooting

### 502 Bad Gateway

Service behind the tunnel is not running. Check on RPi:

```bash
curl -I http://localhost:8080  # OpenHAB
curl -I http://localhost:8081  # Zigbee2MQTT
curl -I http://localhost:3000  # Grafana
```

### DNS Not Resolving

Check propagation:

```bash
dig @8.8.8.8 openhab.fiserhub.fun
# Should return CNAME to 74faf9e3-40ad-49e8-a7c5-409f102bcb7c.cfargotunnel.com
```

If not resolving, flush local DNS cache:

```bash
# macOS
sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder
```

### SSL Handshake Failure

Cloudflare is still generating the SSL certificate. Wait a few minutes after DNS propagation.
Set SSL mode to **Full** in Cloudflare dashboard → SSL/TLS.

### OpenHAB WebSocket Issues

If the OpenHAB UI shows "offline" or items don't update in real-time, try increasing timeouts in `cloudflare/config.yml`:

```yaml
  - hostname: openhab.fiserhub.fun
    service: http://localhost:8080
    originRequest:
      connectTimeout: 30s
```

### Authentication Error (invalid_client)

The Google OAuth Client ID or Secret in Cloudflare Zero Trust doesn't match Google Cloud Console. Verify both match exactly:
- Google Console: https://console.cloud.google.com/apis/credentials
- Cloudflare: Zero Trust → Settings → Authentication → Google

Also verify the redirect URI is exactly:
`https://fiserhub.cloudflareaccess.com/cdn-cgi/access/callback`

### Landing Page Shows Wrong Links

If links point to `.home` instead of `.fiserhub.fun`, the old Nginx config is still deployed. Re-deploy:

```bash
sudo cp cloudflare/nginx-landing.conf /etc/nginx/sites-available/rpi
sudo nginx -t && sudo systemctl reload nginx
```

## Security Notes

- No ports opened on EdgeRouter X — tunnel is outbound-only
- No public IP address required
- MQTT (1883) and InfluxDB (8086) are intentionally NOT exposed
- Tunnel credentials (`~/.cloudflared/` on RPi) must not be committed to git
- Use specific email addresses in Access policies, not email-domain wildcards
- Revoking a user from the policy does not immediately end their session — revoke the session manually in Access Logs
- SSL is terminated at Cloudflare Edge; traffic between Cloudflare and RPi goes through the encrypted tunnel
