# HotspotTrackpad Online Cloud Relay Server

This high-performance WebSocket relay allows your Android phone to control your Mac (trackpad, keyboard, live screen streaming, AI automation) **anywhere over the internet** (across cellular data, different Wi-Fi networks, NAT/firewalls).

---

## ⚡ Option 1: 1-Click Free Deploy to Render (Recommended - 2 Minutes)

Render provides free persistent WebSockets and generous free tier hosting with automatic HTTPS/WSS.

1. **Push this repo or just the `relay-server` folder to GitHub**.
2. Go to [render.com](https://render.com) and click **New + > Web Service**.
3. Connect your GitHub repository.
4. Set the following settings:
   - **Root Directory**: `relay-server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
   - **Plan**: `Free`
5. Click **Create Web Service**.
6. Render will assign you a public URL like:
   `https://hotspot-trackpad-relay.onrender.com`

### Connect Your Devices:
- **On Mac**: In `macOS/relay_config.txt`, set:
  ```
  wss://your-service-name.onrender.com,123456
  ```
  Then run `./macOS/MacTrackpadServer`.
- **On Android**: In the app, switch to **🌐 Online Cloud (Render)**, enter your Render URL and Room PIN (`123456`), and tap **Connect**.

---

## 🛠️ Option 2: Test Locally or on Local Network

1. In terminal:
   ```bash
   cd relay-server
   npm install
   node server.js
   ```
2. The relay server starts on port `10000`.
3. In `macOS/relay_config.txt`:
   ```
   ws://localhost:10000,123456
   ```
4. On Android:
   Enter `ws://<Mac_Local_IP>:10000` with room `123456`.
