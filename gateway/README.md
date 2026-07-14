# DevPulse DeepSeek 中文解读网关

此服务让 App 的 `POST /v1/insights` 请求转发至 DeepSeek，API Key 只存在服务端环境变量中。

## 本地启动

1. 安装 Node.js 20+。
2. 在此目录设置环境变量：`DEEPSEEK_API_KEY`、可选的 `DEEPSEEK_MODEL=deepseek-v4-flash`、`PORT=8787`。
3. 运行：`node server.mjs`。
4. 访问 `http://localhost:8787/health` 确认服务正常。

## 接入 Android

在项目根目录的私有 `local.properties` 写入：

```properties
AI_GATEWAY_URL=https://你的网关域名/
```

不要提交 `local.properties`，也不要把 `DEEPSEEK_API_KEY` 写入 Android 工程或 APK。

网关以 IP 为维度限制为每小时 30 次，并按“仓库 + README”在内存中缓存 7 天。生产部署时应使用 HTTPS，并将缓存与限流迁移至 Redis 或数据库。
