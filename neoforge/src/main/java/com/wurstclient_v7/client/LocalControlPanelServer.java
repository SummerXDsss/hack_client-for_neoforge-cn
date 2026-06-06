package com.wurstclient_v7.client;

import com.wurstclient_v7.config.NeoForgeConfigManager;
import com.wurstclient_v7.feature.ModuleRegistry;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LocalControlPanelServer {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 8180;
    public static final String URL = "http://" + HOST + ":" + PORT + "/";
    private static final String PASSWORD_KEY = "panel.password";
    private static final String DEFAULT_PASSWORD = "mhc8180";

    private static volatile boolean started = false;

    private LocalControlPanelServer() {
    }

    public static void start() {
        if (started) return;

        synchronized (LocalControlPanelServer.class) {
            if (started) return;
            started = true;

            Thread thread = new Thread(LocalControlPanelServer::runServer, "MHC Local Control Panel");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static void openPanel() {
        start();

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(URL));
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", URL});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[] {"open", URL});
            } else {
                Runtime.getRuntime().exec(new String[] {"xdg-open", URL});
            }
        } catch (IOException e) {
            System.out.println("[MHC] Local control panel is available at " + URL);
        }
    }

    private static void runServer() {
        try (ServerSocket server = new ServerSocket(PORT, 16, InetAddress.getByName(HOST))) {
            System.out.println("[MHC] Local control panel started at " + URL);

            while (true) {
                try {
                    Socket socket = server.accept();
                    Thread handler = new Thread(() -> handle(socket), "MHC Local Control Panel Request");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("[MHC] Failed to accept local panel request: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[MHC] Failed to start local control panel at " + URL + ": " + e.getMessage());
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream output = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(separator + 1).trim();
                    headers.put(key, value);
                }
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                send(output, 400, "text/plain; charset=utf-8", "Bad Request");
                return;
            }

            String method = parts[0];
            String path = cleanPath(parts[1]);

            if ("OPTIONS".equals(method)) {
                send(output, 204, "text/plain; charset=utf-8", "");
            } else if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                send(output, 200, "text/html; charset=utf-8", pageHtml());
            } else if ("GET".equals(method) && "/api/modules".equals(path)) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                send(output, 200, "application/json; charset=utf-8", modulesJson());
            } else if ("POST".equals(method) && path.startsWith("/api/modules/") && path.endsWith("/toggle")) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                String id = path.substring("/api/modules/".length(), path.length() - "/toggle".length());
                send(output, 200, "application/json; charset=utf-8", toggleModule(id));
            } else if ("GET".equals(method) && "/favicon.ico".equals(path)) {
                send(output, 204, "text/plain; charset=utf-8", "");
            } else {
                send(output, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            }
        } catch (Exception e) {
            System.err.println("[MHC] Local panel request failed: " + e.getMessage());
        }
    }

    private static String cleanPath(String rawPath) {
        int query = rawPath.indexOf('?');
        String path = query >= 0 ? rawPath.substring(0, query) : rawPath;
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private static boolean hasAccess(Map<String, String> headers) {
        String supplied = headers.getOrDefault("x-mhc-password", "");
        String expected = NeoForgeConfigManager.getString(PASSWORD_KEY, DEFAULT_PASSWORD);
        return supplied.equals(expected);
    }

    private static String modulesJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"modules\":[");
        boolean first = true;

        for (ModuleRegistry.Module module : ModuleRegistry.MODULES.values()) {
            if (!first) json.append(',');
            first = false;
            appendModuleJson(json, module);
        }

        json.append("]}");
        return json.toString();
    }

    private static String toggleModule(String id) {
        ModuleRegistry.Module module = ModuleRegistry.MODULES.get(id);
        if (module == null) {
            return "{\"error\":\"unknown_module\"}";
        }

        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                try {
                    module.toggle();
                    future.complete(module.isEnabled());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });

            future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "{\"error\":\"toggle_failed\",\"message\":\"" + jsonEscape(e.getMessage()) + "\"}";
        }

        StringBuilder json = new StringBuilder();
        appendModuleJson(json, module);
        return json.toString();
    }

    private static void appendModuleJson(StringBuilder json, ModuleRegistry.Module module) {
        json.append('{')
                .append("\"id\":\"").append(jsonEscape(module.id())).append("\",")
                .append("\"name\":\"").append(jsonEscape(displayName(module.id()))).append("\",")
                .append("\"actionKey\":\"").append(jsonEscape(module.actionKey())).append("\",")
                .append("\"enabled\":").append(module.isEnabled())
                .append('}');
    }

    private static String displayName(String id) {
        StringBuilder name = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) name.append(part.substring(1));
        }
        return name.toString();
    }

    private static void send(OutputStream output, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            default -> "OK";
        };
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: http://" + HOST + ":" + PORT + "\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, X-MHC-Password\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String pageHtml() {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>MHC Control Panel</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                  <link rel="stylesheet" href="https://unpkg.com/tdesign-vue-next@1.20.1/dist/tdesign.min.css">
                  <script src="https://unpkg.com/vue@3.5.35/dist/vue.global.prod.js"></script>
                  <script src="https://unpkg.com/tdesign-vue-next@1.20.1/dist/tdesign.min.js"></script>
                  <script>
                    tailwind.config = {
                      theme: {
                        extend: {
                          colors: {
                            mhc: {
                              bg: '#0e1412',
                              panel: '#17211d',
                              line: '#2f3f37',
                              green: '#4ade80',
                              red: '#fb7185',
                              gold: '#facc15'
                            }
                          }
                        }
                      }
                    };
                  </script>
                  <style>
                    :root { color-scheme: dark; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      background: #0e1412;
                    }
                    .t-card {
                      background: #17211d;
                      border-color: #2f3f37;
                    }
                  </style>
                </head>
                <body>
                  <div id="app" class="min-h-screen text-slate-100">
                    <main class="mx-auto flex min-h-screen w-full max-w-6xl items-center px-4 py-8">
                      <section v-if="!unlocked" class="mx-auto w-full max-w-md">
                        <t-card :bordered="true" class="shadow-2xl shadow-black/30">
                          <div class="mb-6">
                            <div class="mb-2 text-xs font-semibold uppercase tracking-wider text-mhc-gold">MHC Local Panel</div>
                            <h1 class="text-2xl font-bold tracking-normal text-white">访问控制</h1>
                            <p class="mt-2 text-sm leading-6 text-slate-400">RightCtrl 只打开本地面板入口，输入访问控制密码后才能管理模块。</p>
                          </div>
                          <t-input
                            v-model="password"
                            type="password"
                            placeholder="访问控制密码"
                            clearable
                            class="mb-4"
                            @enter="unlock"
                          ></t-input>
                          <t-button theme="primary" block :loading="loading" @click="unlock">进入控制面板</t-button>
                          <t-alert v-if="error" theme="error" class="mt-4" :message="error"></t-alert>
                          <p class="mt-4 text-xs leading-5 text-slate-500">服务只监听 127.0.0.1:8180，不对局域网开放。</p>
                        </t-card>
                      </section>

                      <section v-else class="w-full">
                        <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                          <div>
                            <div class="mb-2 text-xs font-semibold uppercase tracking-wider text-mhc-gold">127.0.0.1:8180</div>
                            <h1 class="text-3xl font-bold tracking-normal text-white">MHC Control Panel</h1>
                            <p class="mt-2 text-sm text-slate-400">{{ statusText }}</p>
                          </div>
                          <div class="flex gap-2">
                            <t-button theme="default" @click="refresh" :loading="loading">刷新</t-button>
                            <t-button theme="danger" variant="outline" @click="logout">锁定</t-button>
                          </div>
                        </div>

                        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                          <t-card
                            v-for="module in modules"
                            :key="module.id"
                            :bordered="true"
                            class="min-h-[112px]"
                          >
                            <div class="flex h-full items-start justify-between gap-4">
                              <div class="min-w-0">
                                <div class="break-words text-base font-semibold text-white">{{ module.name }}</div>
                                <div class="mt-2 text-xs text-slate-500">{{ module.id }}</div>
                                <t-tag class="mt-3" :theme="module.enabled ? 'success' : 'danger'" variant="light">
                                  {{ module.enabled ? '已开启' : '已关闭' }}
                                </t-tag>
                              </div>
                              <t-button
                                :theme="module.enabled ? 'success' : 'danger'"
                                :loading="busy[module.id]"
                                @click="toggle(module.id)"
                              >
                                {{ module.enabled ? 'ON' : 'OFF' }}
                              </t-button>
                            </div>
                          </t-card>
                        </div>

                        <t-alert v-if="error" theme="error" class="mt-4" :message="error"></t-alert>
                      </section>
                    </main>
                  </div>
                  <script>
                    const { createApp } = Vue;

                    const app = createApp({
                      data() {
                        return {
                          unlocked: false,
                          password: sessionStorage.getItem('mhc-panel-password') || '',
                          modules: [],
                          busy: {},
                          loading: false,
                          error: '',
                          statusText: '等待同步模块状态'
                        };
                      },
                      mounted() {
                        if (this.password) this.unlock();
                      },
                      methods: {
                        authHeaders() {
                          return { 'X-MHC-Password': this.password };
                        },
                        async unlock() {
                          this.error = '';
                          if (!this.password) {
                            this.error = '请输入访问控制密码';
                            return;
                          }
                          this.loading = true;
                          try {
                            await this.fetchModules();
                            sessionStorage.setItem('mhc-panel-password', this.password);
                            this.unlocked = true;
                            this.statusText = '已连接，模块状态会自动刷新';
                          } catch (error) {
                            sessionStorage.removeItem('mhc-panel-password');
                            this.unlocked = false;
                            this.error = error.message;
                          } finally {
                            this.loading = false;
                          }
                        },
                        async fetchModules() {
                          const response = await fetch('/api/modules', {
                            headers: this.authHeaders(),
                            cache: 'no-store'
                          });
                          if (response.status === 401) throw new Error('访问控制密码错误');
                          if (!response.ok) throw new Error('请求失败：HTTP ' + response.status);
                          const data = await response.json();
                          this.modules = data.modules || [];
                        },
                        async refresh() {
                          this.loading = true;
                          this.error = '';
                          try {
                            await this.fetchModules();
                            this.statusText = '最后刷新：' + new Date().toLocaleTimeString();
                          } catch (error) {
                            this.error = error.message;
                          } finally {
                            this.loading = false;
                          }
                        },
                        async toggle(id) {
                          this.error = '';
                          this.busy = { ...this.busy, [id]: true };
                          try {
                            const response = await fetch('/api/modules/' + encodeURIComponent(id) + '/toggle', {
                              method: 'POST',
                              headers: this.authHeaders(),
                              cache: 'no-store'
                            });
                            if (response.status === 401) throw new Error('访问控制密码错误');
                            if (!response.ok) throw new Error('切换失败：HTTP ' + response.status);
                            await this.fetchModules();
                          } catch (error) {
                            this.error = error.message;
                          } finally {
                            this.busy = { ...this.busy, [id]: false };
                          }
                        },
                        logout() {
                          sessionStorage.removeItem('mhc-panel-password');
                          this.unlocked = false;
                          this.password = '';
                          this.modules = [];
                          this.error = '';
                        }
                      }
                    });

                    app.use(window.TDesign.default || window.TDesign);
                    app.mount('#app');
                  </script>
                </body>
                </html>
                """;
    }
}
