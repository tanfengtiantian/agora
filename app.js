let conn;
const AC = window.WebIM || window.AgoraChat || window.agoraChat;

function log(text) {
  const msgList = document.getElementById("messages");
  const li = document.createElement("li");
  li.textContent = text;
  msgList.appendChild(li);
}

window.addEventListener("load", () => {
  const loginBtn = document.getElementById("loginBtn");
  const getFriendsBtn = document.getElementById("getFriendsBtn");
  const sendBtn = document.getElementById("sendBtn");

  loginBtn.addEventListener("click", () => {
    const appKey = document.getElementById("appKey").value.trim();
    const userId = document.getElementById("userId").value.trim();
    const token = document.getElementById("token").value.trim();

    if (!appKey || !userId || !token) {
      alert("appKey, userId and token required");
      return;
    }

    conn = new AC.connection({
      appKey,
      autoReconnect: true,
    });

    conn.addEventHandler("demo", {
      onTextMessage: (message) => {
        log(`[${message.from}]: ${message.msg}`);
      },
      onConnected: () => {
        log("Connected");
      },
      onDisconnected: () => {
        log("Disconnected");
      }
    });

    conn.open({
      user: userId,
      accessToken: token
    }).catch(err => {
      console.error("login failed", err);
      log("Login failed: " + err.message);
    });
  });

  getFriendsBtn.addEventListener("click", () => {
    if (!conn) {
      alert("Please login first");
      return;
    }
    conn.getRoster().then(roster => {
      const list = document.getElementById("friends");
      list.innerHTML = "";
      roster.forEach(r => {
        if (r.subscription === "both") {
          const li = document.createElement("li");
          li.textContent = r.name;
          list.appendChild(li);
        }
      });
    }).catch(err => {
      console.error("getRoster failed", err);
      log("getRoster failed: " + err.message);
    });
  });

  sendBtn.addEventListener("click", () => {
    if (!conn) {
      alert("Please login first");
      return;
    }
    const toUser = document.getElementById("toUser").value.trim();
    const msgText = document.getElementById("msgText").value;
    if (!toUser || !msgText) {
      alert("toUser and message required");
      return;
    }

    const msg = AC.message.create({
      type: "txt",
      chatType: "singleChat",
      to: toUser,
      msg: msgText
    });

    conn.send(msg).then(() => {
      log(`[me -> ${toUser}]: ${msgText}`);
    }).catch(err => {
      console.error("send failed", err);
      log("send failed: " + err.message);
    });
  });
});
