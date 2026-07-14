# 静态目录数据管道

这是 DevPulse Studio 的低运维数据生产方案：GitHub Actions 定时读取公开仓库，生成可审计的静态目录，再由 Android 下载缓存。它替代客户端高频调用 GitHub Search API，但不会替代用户的本地收藏、搜索记录或学习状态。

## 为什么采用它

- 解决客户端公开 API 限流、榜单不一致和每台设备重复计算的问题。
- 每日目录统一产生，Star 增量与 Release 以真实快照为依据，不伪造“增长榜”。
- AI 密钥只存 GitHub Actions Secret；Android 包和静态 JSON 都不含密钥。
- 适合 V1.1：成本和运维低，后续可迁移到对象存储或函数服务而不改 App 数据模型。

## 仓库配置

1. 在 Actions 设置中允许工作流对仓库具有 `contents: write` 权限。
2. 添加可选变量 `AI_API_BASE_URL` 和 `AI_MODEL`，以及 Secret `AI_API_KEY`。它们使用 OpenAI 兼容的 `/chat/completions` 协议；未配置时只生成真实 GitHub 数据，App 使用本地证据化速读。
3. 手动运行一次 `Generate DevPulse catalog`，确认 `static/catalog.json` 与 `static/daily.json` 被提交。
4. 在 GitHub Pages、Cloudflare R2 或同等静态托管中公开 `static/` 目录。
5. 给 Android 构建配置非机密属性：

```properties
STATIC_CATALOG_BASE_URL=https://<owner>.github.io/<repo>/static/
```

未设置该属性时，App 自动回退为 GitHub API 直连；静态目录不可用时也会回退，不会显示虚构项目。

## 产物

- `catalog.json`：最多 80 个真实 AI 仓库，含基础指标、前一日 Star 差、最新 Release 快照和可选 AI 解读。
- `daily.json`：热门、新晋、Star 增长、当天 Release 的仓库 ID 集合。

生成过程仅保留上一版公共目录用来计算增量；不写入用户收藏、设备标识、搜索或学习状态。

## 运行边界

GitHub Actions 定时任务适合“每日更新”，而不是严格的零点实时 SLA。工作流有 `workflow_dispatch` 手动入口；若未来需要分钟级提醒、推送与更强可用性，应把同一产物协议迁移至函数服务和对象存储。
