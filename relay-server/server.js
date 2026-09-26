const http = require("http");
const { WebSocketServer, WebSocket } = require("ws");

const PORT = process.env.PORT || 10000;

// Store rooms: { roomId: { mac: WebSocket, phone: WebSocket } }
const rooms = new Map();

function getOrCreateRoom(roomId) {
  if (!rooms.has(roomId)) {
    rooms.set(roomId, { mac: null, phone: null });
  }
  return rooms.get(roomId);
}

const server = http.createServer((req, res) => {
  if (req.url === "/" || req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "ok",
        service: "HotspotTrackpad Relay Server",
        activeRooms: rooms.size,
        timestamp: new Date().toISOString()
      })
    );
    return;
  }
  res.writeHead(404, { "Content-Type": "text/plain" });
  res.end("Not Found");
});

const wss = new WebSocketServer({ server });

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const role = url.searchParams.get("role") || "client"; // 'mac' or 'phone'
  const room = url.searchParams.get("room") || "default";

  ws.role = role;
  ws.room = room;
  ws.isAlive = true;

  const currentRoom = getOrCreateRoom(room);

  if (role === "mac") {
    if (currentRoom.mac && currentRoom.mac !== ws) {
      try { currentRoom.mac.close(); } catch (_) {}
    }
    currentRoom.mac = ws;
    console.log(`[Relay] Mac connected to room: ${room}`);
    if (currentRoom.phone && currentRoom.phone.readyState === WebSocket.OPEN) {
      currentRoom.phone.send("RELAY_PEER_CONNECTED,mac");
      ws.send("RELAY_PEER_CONNECTED,phone");
    }
  } else if (role === "phone") {
    if (currentRoom.phone && currentRoom.phone !== ws) {
      try { currentRoom.phone.close(); } catch (_) {}
    }
    currentRoom.phone = ws;
    console.log(`[Relay] Phone connected to room: ${room}`);
    if (currentRoom.mac && currentRoom.mac.readyState === WebSocket.OPEN) {
      ws.send("RELAY_PEER_CONNECTED,mac");
      currentRoom.mac.send("RELAY_PEER_CONNECTED,phone");
    }
  }

  ws.on("pong", () => {
    ws.isAlive = true;
  });

  ws.on("message", (data, isBinary) => {
    const target = role === "mac" ? currentRoom.phone : currentRoom.mac;
    if (target && target.readyState === WebSocket.OPEN) {
      target.send(data, { binary: isBinary });
    }
  });

  ws.on("close", () => {
    console.log(`[Relay] ${role} disconnected from room: ${room}`);
    if (role === "mac" && currentRoom.mac === ws) {
      currentRoom.mac = null;
      if (currentRoom.phone && currentRoom.phone.readyState === WebSocket.OPEN) {
        currentRoom.phone.send("RELAY_PEER_DISCONNECTED,mac");
      }
    } else if (role === "phone" && currentRoom.phone === ws) {
      currentRoom.phone = null;
      if (currentRoom.mac && currentRoom.mac.readyState === WebSocket.OPEN) {
        currentRoom.mac.send("RELAY_PEER_DISCONNECTED,phone");
      }
    }

    if (!currentRoom.mac && !currentRoom.phone) {
      rooms.delete(room);
    }
  });

  ws.on("error", (err) => {
    console.error(`[Relay] Socket error (${role}, room ${room}):`, err.message);
  });
});

// Keep-alive heartbeat every 25 seconds for cloud proxies / Render
const heartbeat = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) {
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 25000);

wss.on("close", () => {
  clearInterval(heartbeat);
});

server.listen(PORT, () => {
  console.log(`[Relay] HotspotTrackpad Relay Server running on port ${PORT}`);
});
