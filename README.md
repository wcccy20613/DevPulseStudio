# DevPulse Studio

开源脉搏（DevPulse Studio）是一款面向中文开发者的 AI 开源项目发现与智能解读 Android 应用。它帮助用户完成发现、理解、收藏与学习，而不是复刻 GitHub 浏览器。

## Highlights

- Kotlin, MVVM, ViewModel, StateFlow and lifecycle-aware UI state
- 真实 GitHub AI 仓库搜索、赛道/新晋时间窗筛选、分页与明确的排序规则
- DeepSeek 中文 README 解读网关：为项目输出中文概述、能力、适用人群、优势、限制和学习优先级
- Room 本地榜单快照、7 天 README 速读缓存和离线收藏学习库
- 本地收藏分类、学习状态、JSON 导入/导出；不启用系统自动云备份
- Material 3 三栏结构：发现、学习库、关于与设置

## Build

Open the project with Android Studio, configure the Android SDK in `local.properties`, then run:

```bash
./gradlew :app:assembleDebug
```

## AI 服务边界

客户端从公开仓库读取 README；可选的服务端网关使用 DeepSeek 生成结构化中文解读。任何模型 API Key 只能配置在网关环境变量中，绝不能写入 Android 工程或 APK。

服务端接口与安全要求见 [AI_GATEWAY_CONTRACT.md](AI_GATEWAY_CONTRACT.md)。

### 部署中文解读网关

网关位于 [gateway](gateway/README.md)，可部署到任意支持 Docker 或 Node.js 20 的平台。部署环境变量：

```bash
DEEPSEEK_API_KEY=你的密钥
DEEPSEEK_MODEL=deepseek-v4-flash
PORT=8787
```

网关部署完成后，在构建 App 时配置：

```bash
./gradlew :app:assembleDebug -PAI_GATEWAY_URL=https://你的网关域名/
```

如不想维护常驻后端，可使用 GitHub Actions 生成并分发每日静态目录，说明见 [STATIC_CATALOG_PIPELINE.md](STATIC_CATALOG_PIPELINE.md)。

## Maintainer

Wang Chunyan (王纯炎) - 2477574245@qq.com

## License

Copyright (c) 2026 Wang Chunyan. Released under the MIT License.
