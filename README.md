# Signal 协议双端示例

本示例演示如何使用 [signalapp/libsignal](https://github.com/signalapp/libsignal) 在浏览器和 Java 程序之间模拟两个客户端的端到端加密通信流程。示例包含：

- `index.html`：基于 `libsignal-protocol.js` 的 Web 页面，负责生成身份密钥、PreKey Bundle，并在浏览器端完成加密与解密。
- `java-client`：使用 Maven 构建的 Java HTTP 服务端，基于 `signal-protocol-java` 实现密钥管理、消息加解密以及向浏览器提供 REST 接口。

## 功能概览

1. 浏览器端生成身份密钥、PreKey Bundle，并通过 `/web/prekey` 接口注册到 Java 端。
2. 浏览器端拉取 Java 端的 PreKey Bundle（`/java/prekey`）并与之建立会话。
3. 浏览器端输入的消息通过 Signal 协议加密后发送到 Java 端（`/messages/from-web`），Java 端解密并返回明文以便演示。
4. Java 端可根据 `/messages/java/send` 请求加密任意明文并存入待取队列，浏览器端通过 `/messages/to-web` 拉取并解密。

## 快速开始

### 1. 启动 Java 服务

```bash
# 进入仓库根目录
mvn -pl java-client exec:java
```

该命令会启动一个监听 `http://localhost:8080` 的 HTTP 服务，并同时提供静态页面 `index.html`。终端会显示可用接口列表。

> 首次执行时 Maven 会从中央仓库下载依赖，请确保网络可访问 Maven Central。

### 2. 打开 Web 页面

在浏览器访问 [http://localhost:8080/](http://localhost:8080/) 即可加载演示页面。页面加载后会自动：

1. 生成浏览器端的身份密钥和 PreKey；
2. 将 PreKey Bundle 发送到 Java 服务；
3. 获取 Java 的 PreKey Bundle 并建立 Signal 会话。

如果需要重新初始化密钥，可点击“重新初始化密钥”按钮。

### 3. 交互流程

- **Web → Java**：在“Web → Java”区域输入文本，点击“加密并发送到 Java”。浏览器会使用 Signal 会话加密消息，通过 `/messages/from-web` 提交。Java 端解密后返回明文，页面日志会显示结果。
- **Java → Web**：在“Java → Web”区域输入文本，点击“让 Java 客户端加密该消息”。服务端会使用现有会话加密并存入队列。随后点击“获取等待 Web 解密的密文”拉取密文并在浏览器端解密，结果显示在日志和下方区域。

## 目录结构

```
├── index.html            # Web 端演示页面
├── java-client           # Java 示例项目
│   ├── pom.xml
│   └── src/main/java/com/example/signal
│       ├── MessageEnvelope.java
│       ├── PreKeyBundleDTO.java
│       ├── SignalClient.java
│       └── SignalServer.java
└── README.md
```

## 注意事项

- 示例仅用于演示 Signal 协议的会话建立及消息收发流程，未考虑长期密钥存储、安全持久化等生产环境需求。
- Web 端脚本直接依赖公共 CDN 上的 `libsignal-protocol.js`，如需离线运行，请将对应脚本下载到本地并修改引用路径。
- Java 服务为简化演示，使用内存存储会话与消息队列，重启服务会导致所有状态丢失。
