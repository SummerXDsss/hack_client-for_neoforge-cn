package com.wurstclient_v7.client;

import com.wurstclient_v7.config.NeoForgeConfigManager;
import com.wurstclient_v7.feature.AutoAttack;
import com.wurstclient_v7.feature.KillAura;
import com.wurstclient_v7.feature.ModuleRegistry;
import com.wurstclient_v7.feature.Nuker;
import com.wurstclient_v7.feature.SpeedHack;
import com.wurstclient_v7.input.KeybindManager;
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
    private static final String INPUT_HUD_KEY = "hud.input.enabled";

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
            if (requestLine == null || requestLine.isBlank()) return;

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
            String rawTarget = parts[1];
            String path = cleanPath(rawTarget);
            Map<String, String> query = queryParams(rawTarget);

            if ("OPTIONS".equals(method)) {
                send(output, 204, "text/plain; charset=utf-8", "");
            } else if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                send(output, 200, "text/html; charset=utf-8", pageHtml());
            } else if ("GET".equals(method) && "/api/modules".equals(path)) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                send(output, 200, "application/json; charset=utf-8", stateJson());
            } else if ("POST".equals(method) && path.startsWith("/api/modules/") && path.endsWith("/toggle")) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                String id = path.substring("/api/modules/".length(), path.length() - "/toggle".length());
                send(output, 200, "application/json; charset=utf-8", toggleModule(id));
            } else if ("POST".equals(method) && path.startsWith("/api/modules/") && path.endsWith("/bind")) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                String id = path.substring("/api/modules/".length(), path.length() - "/bind".length());
                send(output, 200, "application/json; charset=utf-8", bindModule(id, query));
            } else if ("POST".equals(method) && path.startsWith("/api/modules/") && path.endsWith("/clear-bind")) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                String id = path.substring("/api/modules/".length(), path.length() - "/clear-bind".length());
                send(output, 200, "application/json; charset=utf-8", clearBind(id));
            } else if ("POST".equals(method) && path.startsWith("/api/settings/")) {
                if (!hasAccess(headers)) {
                    send(output, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                    return;
                }
                String id = path.substring("/api/settings/".length());
                send(output, 200, "application/json; charset=utf-8", setSetting(id, query));
            } else if ("GET".equals(method) && "/favicon.ico".equals(path)) {
                send(output, 204, "text/plain; charset=utf-8", "");
            } else {
                send(output, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            }
        } catch (Exception e) {
            System.err.println("[MHC] Local panel request failed: " + e.getMessage());
        }
    }

    private static String cleanPath(String rawTarget) {
        int query = rawTarget.indexOf('?');
        String path = query >= 0 ? rawTarget.substring(0, query) : rawTarget;
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private static Map<String, String> queryParams(String rawTarget) {
        Map<String, String> result = new HashMap<>();
        int queryStart = rawTarget.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= rawTarget.length()) return result;

        String query = rawTarget.substring(queryStart + 1);
        for (String part : query.split("&")) {
            if (part.isEmpty()) continue;
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            result.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    private static boolean hasAccess(Map<String, String> headers) {
        String supplied = headers.getOrDefault("x-mhc-password", "");
        String expected = NeoForgeConfigManager.getString(PASSWORD_KEY, DEFAULT_PASSWORD);
        return supplied.equals(expected);
    }

    private static String stateJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"modules\":[");
        boolean first = true;
        for (ModuleRegistry.Module module : ModuleRegistry.MODULES.values()) {
            if (!first) json.append(',');
            first = false;
            appendModuleJson(json, module);
        }
        json.append("],\"settings\":[");
        appendBooleanSetting(json, true, INPUT_HUD_KEY, "按键显示", NeoForgeConfigManager.getBoolean(INPUT_HUD_KEY, false));
        appendNumberSetting(json, false, "speed.multiplier", "速度倍率", SpeedHack.getMultiplier(), 1.0, 5.0, 0.1);
        appendNumberSetting(json, false, "killaura.range", "KillAura 范围", KillAura.getRange(), 1.0, 8.0, 0.1);
        appendNumberSetting(json, false, "autoattack.range", "自动攻击范围", AutoAttack.getRange(), 1.0, 10.0, 1.0);
        appendNumberSetting(json, false, "nuker.range", "自动挖掘范围", Nuker.getRange(), 1.0, 8.0, 1.0);
        json.append("]}");
        return json.toString();
    }

    private static String toggleModule(String id) {
        ModuleRegistry.Module module = ModuleRegistry.MODULES.get(id);
        if (module == null) return "{\"error\":\"unknown_module\"}";

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

        return stateJson();
    }

    private static String bindModule(String id, Map<String, String> query) {
        ModuleRegistry.Module module = ModuleRegistry.MODULES.get(id);
        if (module == null) return "{\"error\":\"unknown_module\"}";

        try {
            int key = Integer.parseInt(query.getOrDefault("key", "-1"));
            int modifiers = Integer.parseInt(query.getOrDefault("modifiers", "0"));
            boolean mouse = Boolean.parseBoolean(query.getOrDefault("mouse", "false"));
            if (mouse && key == 0) return "{\"error\":\"left_mouse_reserved\"}";
            if (key < 0) return "{\"error\":\"invalid_key\"}";
            KeybindManager.setKey(module.actionKey(), key, modifiers, mouse);
        } catch (NumberFormatException e) {
            return "{\"error\":\"invalid_key\"}";
        }

        return stateJson();
    }

    private static String clearBind(String id) {
        ModuleRegistry.Module module = ModuleRegistry.MODULES.get(id);
        if (module == null) return "{\"error\":\"unknown_module\"}";
        KeybindManager.clear(module.actionKey());
        return stateJson();
    }

    private static String setSetting(String id, Map<String, String> query) {
        String value = query.getOrDefault("value", "");
        try {
            switch (id) {
                case INPUT_HUD_KEY -> NeoForgeConfigManager.setBoolean(INPUT_HUD_KEY, Boolean.parseBoolean(value));
                case "speed.multiplier" -> SpeedHack.setMultiplier(Double.parseDouble(value));
                case "killaura.range" -> KillAura.setRange(Double.parseDouble(value));
                case "autoattack.range" -> AutoAttack.setRange((int)Math.round(Double.parseDouble(value)));
                case "nuker.range" -> Nuker.setRange((int)Math.round(Double.parseDouble(value)));
                default -> {
                    return "{\"error\":\"unknown_setting\"}";
                }
            }
        } catch (NumberFormatException e) {
            return "{\"error\":\"invalid_value\"}";
        }
        return stateJson();
    }

    private static void appendModuleJson(StringBuilder json, ModuleRegistry.Module module) {
        json.append('{')
                .append("\"id\":\"").append(jsonEscape(module.id())).append("\",")
                .append("\"name\":\"").append(jsonEscape(displayName(module.id()))).append("\",")
                .append("\"actionKey\":\"").append(jsonEscape(module.actionKey())).append("\",")
                .append("\"keyLabel\":\"").append(jsonEscape(KeybindManager.getLabel(module.actionKey()))).append("\",")
                .append("\"enabled\":").append(module.isEnabled())
                .append('}');
    }

    private static void appendBooleanSetting(StringBuilder json, boolean first, String id, String label, boolean value) {
        if (!first) json.append(',');
        json.append('{')
                .append("\"id\":\"").append(jsonEscape(id)).append("\",")
                .append("\"label\":\"").append(jsonEscape(label)).append("\",")
                .append("\"type\":\"boolean\",")
                .append("\"value\":").append(value)
                .append('}');
    }

    private static void appendNumberSetting(StringBuilder json, boolean first, String id, String label, double value, double min, double max, double step) {
        if (!first) json.append(',');
        json.append('{')
                .append("\"id\":\"").append(jsonEscape(id)).append("\",")
                .append("\"label\":\"").append(jsonEscape(label)).append("\",")
                .append("\"type\":\"number\",")
                .append("\"value\":").append(value).append(',')
                .append("\"min\":").append(min).append(',')
                .append("\"max\":").append(max).append(',')
                .append("\"step\":").append(step)
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
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>MHC 控制面板</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                  <link rel="stylesheet" href="https://unpkg.com/tdesign-vue-next@1.20.1/dist/tdesign.min.css">
                  <script src="https://unpkg.com/vue@3.5.35/dist/vue.global.prod.js"></script>
                  <script src="https://unpkg.com/tdesign-vue-next@1.20.1/dist/tdesign.min.js"></script>
                  <script>
                    tailwind.config = { theme: { extend: { colors: { mhc: { bg: '#0e1412', panel: '#17211d', line: '#2f3f37', gold: '#facc15' } } } } };
                  </script>
                  <style>
                    :root { color-scheme: dark; }
                    body { margin: 0; min-height: 100vh; background: #0e1412; }
                    .t-card { background: #17211d; border-color: #2f3f37; }
                  </style>
                </head>
                <body>
                  <div id="app" class="min-h-screen text-slate-100">
                    <main class="mx-auto min-h-screen w-full max-w-6xl px-4 py-8">
                      <section v-if="!unlocked" class="mx-auto flex min-h-[80vh] w-full max-w-md items-center">
                        <t-card :bordered="true" class="w-full shadow-2xl shadow-black/30">
                          <div class="mb-6">
                            <div class="mb-2 text-xs font-semibold uppercase tracking-wider text-mhc-gold">MHC 本地面板</div>
                            <h1 class="text-2xl font-bold tracking-normal text-white">访问验证</h1>
                            <p class="mt-2 text-sm leading-6 text-slate-400">输入本地控制密码来管理模块。</p>
                          </div>
                          <t-input v-model="password" type="password" placeholder="密码" clearable class="mb-4" @enter="unlock"></t-input>
                          <t-button theme="primary" block :loading="loading" @click="unlock">解锁</t-button>
                          <t-alert v-if="error" theme="error" class="mt-4" :message="error"></t-alert>
                          <p class="mt-4 text-xs leading-5 text-slate-500">默认密码：mhc8180。服务只监听 127.0.0.1:8180。</p>
                        </t-card>
                      </section>

                      <section v-else>
                        <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                          <div>
                            <div class="mb-2 text-xs font-semibold uppercase tracking-wider text-mhc-gold">127.0.0.1:8180</div>
                            <h1 class="text-3xl font-bold tracking-normal text-white">MHC 控制面板</h1>
                            <p class="mt-2 text-sm text-slate-400">{{ statusText }}</p>
                          </div>
                          <div class="flex gap-2">
                            <t-button theme="default" @click="refresh" :loading="loading">刷新</t-button>
                            <t-button theme="danger" variant="outline" @click="logout">锁定</t-button>
                          </div>
                        </div>

                        <t-card :bordered="true" class="mb-4">
                          <div class="mb-3 text-base font-semibold text-white">全局设置</div>
                          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                            <div v-for="setting in settings" :key="setting.id" class="rounded-md border border-slate-700/70 p-3">
                              <div class="mb-2 text-sm font-semibold text-slate-100">{{ setting.label }}</div>
                              <template v-if="setting.type === 'boolean'">
                                <t-switch :model-value="setting.value" @change="value => updateSetting(setting, value)"></t-switch>
                              </template>
                              <template v-else>
                                <div class="flex items-center gap-3">
                                  <t-slider class="flex-1" :model-value="setting.value" :min="setting.min" :max="setting.max" :step="setting.step" @change="value => updateSetting(setting, value)"></t-slider>
                                  <t-input-number :model-value="setting.value" :min="setting.min" :max="setting.max" :step="setting.step" size="small" class="w-28" @change="value => updateSetting(setting, value)"></t-input-number>
                                </div>
                              </template>
                            </div>
                          </div>
                        </t-card>

                        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                          <t-card v-for="module in modules" :key="module.id" :bordered="true" class="min-h-[152px]">
                            <div class="flex h-full flex-col gap-3">
                              <div class="flex items-start justify-between gap-4">
                                <div class="min-w-0">
                                  <div class="break-words text-base font-semibold text-white">{{ module.name }}</div>
                                  <div class="mt-1 text-xs text-slate-500">{{ module.id }}</div>
                                </div>
                                <t-button :theme="module.enabled ? 'success' : 'danger'" :loading="busy[module.id]" @click="toggle(module.id)">
                                  {{ module.enabled ? '开' : '关' }}
                                </t-button>
                              </div>
                              <div class="rounded-md border border-slate-700/70 p-2">
                                <div class="mb-2 text-xs text-slate-400">快捷键：<span class="text-slate-100">{{ module.keyLabel }}</span></div>
                                <div class="flex flex-wrap gap-2">
                                  <t-button size="small" theme="default" @click="listenBind(module)">{{ listeningId === module.id ? '请按键...' : '修改按键' }}</t-button>
                                  <t-button size="small" theme="danger" variant="outline" @click="clearBind(module.id)">清除</t-button>
                                </div>
                              </div>
                            </div>
                          </t-card>
                        </div>

                        <t-alert v-if="error" theme="error" class="mt-4" :message="error"></t-alert>
                      </section>
                    </main>
                  </div>
                  <script>
                    const { createApp } = Vue;

                    const GLFW = {
                      Backspace: 259, Tab: 258, Enter: 257, Escape: 256, Space: 32,
                      ArrowRight: 262, ArrowLeft: 263, ArrowDown: 264, ArrowUp: 265,
                      ShiftLeft: 340, ControlLeft: 341, AltLeft: 342, MetaLeft: 343,
                      ShiftRight: 344, ControlRight: 345, AltRight: 346, MetaRight: 347,
                      Insert: 260, Delete: 261, Home: 268, End: 269, PageUp: 266, PageDown: 267,
                      CapsLock: 280
                    };
                    for (let i = 1; i <= 12; i++) GLFW['F' + i] = 289 + i;
                    for (let i = 0; i <= 9; i++) GLFW['Digit' + i] = 48 + i;
                    for (let i = 0; i < 26; i++) GLFW['Key' + String.fromCharCode(65 + i)] = 65 + i;

                    const app = createApp({
                      data() {
                        return {
                          unlocked: false,
                          password: sessionStorage.getItem('mhc-panel-password') || '',
                          modules: [],
                          settings: [],
                          busy: {},
                          loading: false,
                          error: '',
                          statusText: '等待模块状态',
                          listeningId: ''
                        };
                      },
                      mounted() {
                        window.addEventListener('keydown', this.onKeyDown);
                        if (this.password) this.unlock();
                      },
                      beforeUnmount() {
                        window.removeEventListener('keydown', this.onKeyDown);
                      },
                      methods: {
                        authHeaders() {
                          return { 'X-MHC-Password': this.password };
                        },
                        async unlock() {
                          this.error = '';
                          if (!this.password) {
                            this.error = '请输入密码';
                            return;
                          }
                          this.loading = true;
                          try {
                            await this.fetchState();
                            sessionStorage.setItem('mhc-panel-password', this.password);
                            this.unlocked = true;
                            this.statusText = '已连接';
                          } catch (error) {
                            sessionStorage.removeItem('mhc-panel-password');
                            this.unlocked = false;
                            this.error = error.message;
                          } finally {
                            this.loading = false;
                          }
                        },
                        async request(url, options = {}) {
                          const response = await fetch(url, {
                            ...options,
                            headers: { ...this.authHeaders(), ...(options.headers || {}) },
                            cache: 'no-store'
                          });
                          if (response.status === 401) throw new Error('密码错误');
                          if (!response.ok) throw new Error('HTTP ' + response.status);
                          return await response.json();
                        },
                        applyState(data) {
                          this.modules = data.modules || [];
                          this.settings = data.settings || [];
                        },
                        async fetchState() {
                          this.applyState(await this.request('/api/modules'));
                        },
                        async refresh() {
                          this.loading = true;
                          this.error = '';
                          try {
                            await this.fetchState();
                            this.statusText = '上次刷新：' + new Date().toLocaleTimeString();
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
                            this.applyState(await this.request('/api/modules/' + encodeURIComponent(id) + '/toggle', { method: 'POST' }));
                          } catch (error) {
                            this.error = error.message;
                          } finally {
                            this.busy = { ...this.busy, [id]: false };
                          }
                        },
                        listenBind(module) {
                          this.error = '';
                          this.listeningId = module.id;
                        },
                        async onKeyDown(event) {
                          if (!this.listeningId) return;
                          event.preventDefault();
                          const key = GLFW[event.code];
                          if (key === undefined) {
                            this.error = '不支持的按键：' + event.code;
                            return;
                          }
                          const modifierCodes = ['ShiftLeft', 'ShiftRight', 'ControlLeft', 'ControlRight', 'AltLeft', 'AltRight', 'MetaLeft', 'MetaRight'];
                          let modifiers = 0;
                          if (event.shiftKey && !modifierCodes.includes(event.code)) modifiers |= 1;
                          if (event.ctrlKey && !modifierCodes.includes(event.code)) modifiers |= 2;
                          if (event.altKey && !modifierCodes.includes(event.code)) modifiers |= 4;
                          if (event.metaKey && !modifierCodes.includes(event.code)) modifiers |= 8;
                          const id = this.listeningId;
                          this.listeningId = '';
                          try {
                            this.applyState(await this.request('/api/modules/' + encodeURIComponent(id) + '/bind?key=' + key + '&modifiers=' + modifiers + '&mouse=false', { method: 'POST' }));
                          } catch (error) {
                            this.error = error.message;
                          }
                        },
                        async clearBind(id) {
                          this.error = '';
                          try {
                            this.applyState(await this.request('/api/modules/' + encodeURIComponent(id) + '/clear-bind', { method: 'POST' }));
                          } catch (error) {
                            this.error = error.message;
                          }
                        },
                        async updateSetting(setting, value) {
                          this.error = '';
                          try {
                            this.applyState(await this.request('/api/settings/' + encodeURIComponent(setting.id) + '?value=' + encodeURIComponent(value), { method: 'POST' }));
                          } catch (error) {
                            this.error = error.message;
                          }
                        },
                        logout() {
                          sessionStorage.removeItem('mhc-panel-password');
                          this.unlocked = false;
                          this.password = '';
                          this.modules = [];
                          this.settings = [];
                          this.error = '';
                          this.listeningId = '';
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
