const WebSocket = require("ws");

const WS_URL = "ws://127.0.0.1:8080";
const DEVICE_ID = "conecta-tv-rustdesk";
const AUTH_TOKEN = "b2136c040c88c7237ec6450c97cfad9b4307cb9bcc2e0192c61be61d004d6427";

const ws = new WebSocket(WS_URL);
let commandsSent = false;
let streamStarted = false;
let nextCommandId = 1;
let frameCount = 0;
let frameBytes = 0;
let lastFrameAt = 0;
let startedAt = 0;

function createCommandId() {
  const id = `cmd-${String(nextCommandId).padStart(3, "0")}`;
  nextCommandId += 1;
  return id;
}

function send(payload) {
  const raw = JSON.stringify(payload);
  console.log(">>>", raw.length > 400 ? `${raw.slice(0, 400)}...[truncated]` : raw);
  ws.send(raw);
}

function sendRemoteCommand(commandPayload) {
  const payload = {
    type: "remote-command",
    commandId: createCommandId(),
    token: AUTH_TOKEN,
    ...commandPayload
  };
  send(payload);
}

function sendStartStream() {
  startedAt = Date.now();
  send({
    type: "start-stream",
    deviceId: DEVICE_ID,
    token: AUTH_TOKEN
  });
}

function sendStopStream() {
  send({
    type: "stop-stream",
    deviceId: DEVICE_ID,
    token: AUTH_TOKEN
  });
}

ws.on("open", () => {
  console.log("controller connected");

  send({
    type: "hello",
    role: "controller",
    deviceId: DEVICE_ID,
    token: AUTH_TOKEN
  });

  setTimeout(() => {
    send({
      type: "pair",
      deviceId: DEVICE_ID,
      token: AUTH_TOKEN
    });
  }, 300);
});

ws.on("message", (data) => {
  const text = data.toString();
  console.log("<<<", text.length > 400 ? `${text.slice(0, 400)}...[truncated]` : text);

  let json;
  try {
    json = JSON.parse(text);
  } catch {
    return;
  }

  if (json.type === "remote-command-ack") {
    console.log(
      `ack commandId=${json.commandId} command=${json.command} status=${json.status}` +
      (json.error ? ` error=${json.error}` : "")
    );
    return;
  }

  if (json.type === "frame") {
    frameCount += 1;
    lastFrameAt = Date.now();
    const size = json.imageBase64 ? json.imageBase64.length : 0;
    frameBytes += size;
    console.log(`frame #${frameCount} bytes(base64)=${size} ${json.width}x${json.height} ts=${json.ts}`);
    return;
  }

  if (json.type === "create-offer") {
    console.log("controller received create-offer");

    if (!streamStarted) {
      streamStarted = true;
      setTimeout(() => {
        sendStartStream();
      }, 500);
    }

    if (commandsSent) {
      console.log("commands already sent; ignoring duplicate create-offer");
      return;
    }

    commandsSent = true;

    setTimeout(() => sendRemoteCommand({ command: "home" }), 1000);
    setTimeout(() => sendRemoteCommand({ command: "back" }), 2000);
    setTimeout(() => {
      sendRemoteCommand({
        command: "tap",
        x: 500,
        y: 300,
        durationMs: 100
      });
    }, 3000);
    setTimeout(() => {
      sendRemoteCommand({
        command: "swipe",
        x1: 500,
        y1: 800,
        x2: 500,
        y2: 300,
        durationMs: 300
      });
    }, 4000);

    setTimeout(() => {
      sendStopStream();
    }, 15000);
  }
});

ws.on("close", () => {
  console.log("controller disconnected");
});

ws.on("error", (err) => {
  console.log("controller error:", err.message);
});

setInterval(() => {
  if (!streamStarted) return;
  const age = lastFrameAt ? Date.now() - lastFrameAt : -1;
  const elapsedSec = startedAt ? Math.max(1, (Date.now() - startedAt) / 1000) : 1;
  const fps = (frameCount / elapsedSec).toFixed(2);
  const avgBytes = frameCount > 0 ? Math.round(frameBytes / frameCount) : 0;
  console.log(`stream monitor frameCount=${frameCount} lastFrameAgeMs=${age} fps=${fps} avgBase64Bytes=${avgBytes}`);
}, 5000);