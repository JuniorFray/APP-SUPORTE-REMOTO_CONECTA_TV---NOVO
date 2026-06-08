const WebSocket = require("ws");

const PORT = 8080;
const HOST = "0.0.0.0";
const VALID_TOKEN = "b2136c040c88c7237ec6450c97cfad9b4307cb9bcc2e0192c61be61d004d6427";

const peersByDeviceId = new Map();

function safeSend(ws, payload) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function attachPeer(ws, info) {
  ws.peerInfo = {
    role: info.role || "unknown",
    deviceId: info.deviceId || null,
    token: info.token || null,
    paired: false
  };

  if (ws.peerInfo.deviceId) {
    if (!peersByDeviceId.has(ws.peerInfo.deviceId)) {
      peersByDeviceId.set(ws.peerInfo.deviceId, { host: null, controller: null });
    }

    const slot = peersByDeviceId.get(ws.peerInfo.deviceId);
    if (ws.peerInfo.role === "host") slot.host = ws;
    if (ws.peerInfo.role === "controller") slot.controller = ws;
  }
}

function detachPeer(ws) {
  const info = ws.peerInfo;
  if (!info || !info.deviceId) return;

  const slot = peersByDeviceId.get(info.deviceId);
  if (!slot) return;

  if (slot.host === ws) slot.host = null;
  if (slot.controller === ws) slot.controller = null;

  if (!slot.host && !slot.controller) {
    peersByDeviceId.delete(info.deviceId);
  }
}

function getPeerPair(deviceId) {
  if (!deviceId) return null;
  return peersByDeviceId.get(deviceId) || null;
}

function getOtherPeer(ws) {
  const info = ws.peerInfo;
  if (!info || !info.deviceId) return null;

  const pair = getPeerPair(info.deviceId);
  if (!pair) return null;

  if (info.role === "host") return pair.controller;
  if (info.role === "controller") return pair.host;
  return null;
}

const FORWARDED_TYPES = new Set([
  "offer",
  "answer",
  "candidate",
  "remote-command",
  "remote-command-ack",
  "start-stream",
  "stop-stream",
  "frame",
  "frame-ack"
]);

const wss = new WebSocket.Server({ host: HOST, port: PORT });

wss.on("listening", () => {
  console.log(`WebSocket signaling server on ws://${HOST}:${PORT}`);
});

wss.on("connection", (ws, req) => {
  console.log("client connected:", req.socket.remoteAddress);
  ws.peerInfo = null;

  ws.on("message", (msg) => {
    const raw = msg.toString();
    console.log("message:", raw.length > 500 ? `${raw.slice(0, 500)}...[truncated]` : raw);

    let json;
    try {
      json = JSON.parse(raw);
    } catch (e) {
      safeSend(ws, { type: "error", message: "invalid-json" });
      return;
    }

    const type = json.type;
    const token = json.token;

    if (token && token !== VALID_TOKEN) {
      safeSend(ws, { type: "error", message: "invalid-token" });
      return;
    }

    if (type === "hello") {
      attachPeer(ws, json);
      safeSend(ws, {
        type: "ack",
        stage: "hello",
        role: ws.peerInfo?.role || null,
        deviceId: ws.peerInfo?.deviceId || null
      });
      return;
    }

    if (type === "pair") {
      if (!ws.peerInfo) {
        safeSend(ws, { type: "error", message: "hello-required" });
        return;
      }

      const otherPeer = getOtherPeer(ws);
      if (!otherPeer) {
        safeSend(ws, {
          type: "waiting-peer",
          deviceId: ws.peerInfo.deviceId,
          role: ws.peerInfo.role
        });
        return;
      }

      ws.peerInfo.paired = true;
      if (otherPeer.peerInfo) otherPeer.peerInfo.paired = true;

      safeSend(ws, {
        type: "paired",
        deviceId: ws.peerInfo.deviceId,
        peerRole: otherPeer.peerInfo?.role || null
      });

      safeSend(otherPeer, {
        type: "paired",
        deviceId: ws.peerInfo.deviceId,
        peerRole: ws.peerInfo.role
      });

      if (ws.peerInfo.role === "controller") {
        safeSend(ws, {
          type: "create-offer",
          deviceId: ws.peerInfo.deviceId
        });
      } else if (otherPeer.peerInfo?.role === "controller") {
        safeSend(otherPeer, {
          type: "create-offer",
          deviceId: ws.peerInfo.deviceId
        });
      }

      return;
    }

    if (FORWARDED_TYPES.has(type)) {
      if (!ws.peerInfo) {
        safeSend(ws, { type: "error", message: "hello-required" });
        return;
      }

      const otherPeer = getOtherPeer(ws);
      if (!otherPeer) {
        safeSend(ws, { type: "error", message: "peer-not-found" });
        return;
      }

      safeSend(otherPeer, json);
      return;
    }

    safeSend(ws, { type: "ack", stage: type || "unknown" });
  });

  ws.on("close", () => {
    console.log("client disconnected");
    detachPeer(ws);
  });

  ws.on("error", (err) => {
    console.log("socket error:", err.message);
    detachPeer(ws);
  });
});