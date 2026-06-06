# 更新日志

## 2026-06-06

### 稳定性修复

- 移除会导致闪退的 `Health Tags` 功能。
  - 删除网页面板中的 `health_tags` 模块入口。
  - 删除 `HealthTagsMain`。
  - 删除实体渲染血量标签 mixin。
  - 删除相关配置项和语言项。
  - 已确认构建后的 jar 内没有 `HealthTags`、`HealthTag`、`EntityRendererMixin` 残留。
- 修复鼠标左键检测导致的 `65539: Invalid key 0` 刷屏问题。
  - 鼠标按钮检测统一改为 `glfwGetMouseButton`。
  - 左键不再被当作键盘按键检测。
- 移除会导致崩溃的 `LSD` 功能。
  - 删除 `LsdHack`。
  - 删除相关渲染 mixin 和 shader 资源。
- 移除旧的 `WorldRendererMixin` 残留，修复启动阶段潜在崩溃。
- 移除 `health_tag` 网络 payload 握手，避免客户端/服务器协议不匹配。
- 修复 `SafeWalk` 强制按下 Shift 的问题。
- 修复鼠标左键误触发模块开关的问题。

### 网页面板

- 新增本地网页控制面板：`http://127.0.0.1:8180/`。
  - 默认密码：`mhc8180`。
  - 仅监听本机地址 `127.0.0.1`。
- 网页面板已中文化。
  - 翻译全局设置、快捷键、刷新、锁定、范围设置等页面文字。
- 新增网页设置项。
  - 按键显示开关，默认关闭。
  - Speed 倍率调节。
  - KillAura 范围调节。
  - AutoAttack 范围调节。
  - Nuker 范围调节。
- 支持在网页中修改和清除模块快捷键。

### 发布包

- 最新构建产物：`dist/HackClient-1.21.1.jar`
- 最近提交：
  - `c733dfc` Remove crashing health tags module
  - `5262a44` Fix mouse button polling
  - `00c1129` Translate local panel settings
  - `c3a9e0b` Remove LSD and add panel settings
  - `15f8864` Prevent left click from toggling modules
  - `c72584d` Stop SafeWalk from forcing sneak key
  - `0bf8ce8` Add password protected local control panel
  - `a0ce665` Remove health tag payload handshake
  - `d59681b` Fix startup crash from stale world renderer mixin
