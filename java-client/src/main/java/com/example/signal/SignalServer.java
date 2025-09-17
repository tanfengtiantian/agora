package com.example.signal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Small HTTP server that exposes endpoints for the browser demo to interact with the Java Signal client.
 */
public class SignalServer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final SignalClient javaClient = new SignalClient("java");
    private final List<MessageEnvelope> pendingForWeb = new CopyOnWriteArrayList<>();
    private final Path webRoot = determineWebRoot();

    private volatile PreKeyBundleDTO webPreKeyBundle;

    public static void main(String[] args) throws IOException {
        SignalServer server = new SignalServer();
        server.start(8080);
        System.out.println("Signal demo server started on http://localhost:8080");
        System.out.println("Open http://localhost:8080/ in a browser to load the web demo.");
        System.out.println("Available API endpoints:");
        System.out.println("GET  /java/prekey        -> Java client's pre-key bundle");
        System.out.println("POST /web/prekey         -> Submit web client's pre-key bundle");
        System.out.println("POST /messages/from-web  -> Encrypted message destined for Java");
        System.out.println("POST /messages/java/send -> Ask Java client to encrypt a message for the web client");
        System.out.println("GET  /messages/to-web    -> Retrieve encrypted messages waiting for the web client");
    }

    public void start(int port) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/", new StaticFileHandler());
        httpServer.createContext("/java/prekey", new JavaPreKeyHandler());
        httpServer.createContext("/web/prekey", new WebPreKeyHandler());
        httpServer.createContext("/messages/from-web", new WebMessageHandler());
        httpServer.createContext("/messages/java/send", new JavaSendHandler());
        httpServer.createContext("/messages/to-web", new WebQueueHandler());
        httpServer.setExecutor(Executors.newFixedThreadPool(4));
        httpServer.start();
    }

    private class JavaPreKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            PreKeyBundleDTO bundle = javaClient.generatePreKeyBundle();
            sendJson(exchange, 200, GSON.toJson(bundle));
        }
    }

    private class WebPreKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            PreKeyBundleDTO bundle = GSON.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), PreKeyBundleDTO.class);
            webPreKeyBundle = bundle;
            javaClient.processPreKeyBundle("web", bundle);
            sendText(exchange, 204, "");
        }
    }

    private class WebMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            MessageEnvelope envelope = GSON.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), MessageEnvelope.class);
            String plaintext = javaClient.decrypt("web", envelope);
            JsonObject response = new JsonObject();
            response.addProperty("plaintext", plaintext);
            sendJson(exchange, 200, GSON.toJson(response));
        }
    }

    private class JavaSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            JsonObject payload = JsonParser.parseReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)).getAsJsonObject();
            String plaintext = payload.get("plaintext").getAsString();
            if (webPreKeyBundle == null) {
                sendText(exchange, 428, "Web pre-key bundle not yet registered");
                return;
            }
            MessageEnvelope envelope = javaClient.encrypt("web", plaintext);
            pendingForWeb.add(envelope);
            sendJson(exchange, 200, GSON.toJson(envelope));
        }
    }

    private class WebQueueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            List<MessageEnvelope> toDeliver = new ArrayList<>(pendingForWeb);
            pendingForWeb.clear();
            sendJson(exchange, 200, GSON.toJson(toDeliver));
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath == null || "/".equals(requestPath)) {
                requestPath = "/index.html";
            }
            Path file = resolveStaticPath(requestPath);
            if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            byte[] data = Files.readAllBytes(file);
            String contentType = requestPath.endsWith(".html") ? "text/html" : "text/plain";
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }

    private Path resolveStaticPath(String requestPath) {
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        Path candidate = webRoot.resolve(requestPath).normalize();
        if (!candidate.startsWith(webRoot)) {
            return null;
        }
        return candidate;
    }

    private Path determineWebRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("index.html"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("index.html"))) {
            return parent;
        }
        return cwd;
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}
