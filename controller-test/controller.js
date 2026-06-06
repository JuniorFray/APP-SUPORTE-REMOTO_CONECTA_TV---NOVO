const WebSocket = require("ws");

const WS_URL = "ws://127.0.0.1:8080";
const DEVICE_ID = "conecta-tv-rustdesk";
const AUTH_TOKEN = "b2136c040c88c7237ec6450c97cfad9b4307cb9bcc2e0192c61be61d004d6427";

const ws = new WebSocket(WS_URL);

function send(payload) {
  const raw = JSON.stringify(payload);
  console.log(">>>", raw);
  ws.send(raw);
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
  console.log("<<<", text);

  let json;
  try {
    json = JSON.parse(text);
  } catch {
    return;
  }

  if (json.type === "create-offer") {
    console.log("controller received create-offer");
  }
});

ws.on("close", () => {
  console.log("controller disconnected");
});

ws.on("error", (err) => {
  console.log("controller error:", err.message);
});
