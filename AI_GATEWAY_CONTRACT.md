# DevPulse Studio AI Gateway Contract

此契约用于接入可信的云端 README 解读；它不是可选的“聊天接口”。网关负责服务端密钥、限流、内容安全、模型版本与可观测性，Android 客户端不保存模型提供方密钥。

## 启用方式

在开发机或 CI 的 Gradle 属性中配置（不要提交到仓库）：

```properties
AI_GATEWAY_URL=https://api.example.com/
```

未配置时，App 自动使用本地 README 证据化速读；网络失败也回退到本地速读并保留缓存。

## `POST /v1/insights`

请求只包含公开 GitHub 仓库及其 README，不包含收藏、搜索历史、学习状态或设备标识。

```json
{
  "repository": "owner/repository",
  "repositoryUrl": "https://github.com/owner/repository",
  "language": "Python",
  "topics": ["agent", "llm"],
  "readme": "...最多 60,000 字符的公开 README..."
}
```

成功响应：

```json
{
  "oneLiner": "一句可验证的项目概述",
  "capabilities": ["能力一", "能力二"],
  "audience": "适合的人群与前提",
  "strengths": "公开证据支持的优势",
  "limitations": "风险、缺失证据或使用限制",
  "score": 7,
  "evidence": "README/许可证/更新日期等可追溯依据",
  "modelVersion": "insight-v1"
}
```

## 不可违反的服务端规则

- 只根据请求中的 README 和可核验仓库元数据作答；证据不足时明确说明，而不是补全事实。
- `score` 仅代表学习优先级，不得表达安全、合规、投资或生产可用性保证。
- 返回内容必须包含 `limitations` 与 `evidence`，不得省略风险边界。
- 面向中文客户端的 `oneLiner`、`capabilities`、`audience`、`strengths`、`limitations` 与 `evidence` 必须使用简体中文。
- 网关必须对单仓库请求去重缓存 7 天，并按用户/设备匿名限流；不能把模型密钥、供应商错误或内部提示词返回给客户端。
- 对 DeepFake、换脸、身份合成等高风险仓库返回额外的合规提醒；客户端仍保留独立的风险弹窗。
- 记录最小化：仅保留服务运行所需的匿名指标和短期错误日志，不建立用户学习画像；若未来要做云端个性化推荐，必须先获得明确授权。

## 后续接口

每日精选、Star 增长、Release 更新应由同一网关的定时快照任务生成，再向客户端下发已缓存结果。客户端不得针对每个收藏仓库高频轮询公开 GitHub API，以免耗电、限流和产生不稳定提醒。
