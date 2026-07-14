import { createHash } from "node:crypto";
import http from "node:http";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.DEEPSEEK_API_KEY;
const model = process.env.DEEPSEEK_MODEL || "deepseek-v4-flash";
const endpoint = "https://api.deepseek.com/chat/completions";
const cache = new Map();
const requestsByIp = new Map();
const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const RATE_WINDOW_MS = 60 * 60 * 1000;
const RATE_LIMIT = 30;
const INSIGHT_SCHEMA_VERSION = 2;

if (!apiKey) {
  throw new Error("DEEPSEEK_API_KEY is required");
}

function json(response, status, payload) {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.on("data", (chunk) => {
      body += chunk;
      if (body.length > 512_000) request.destroy();
    });
    request.on("end", () => {
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(new Error("invalid_json"));
      }
    });
    request.on("error", reject);
  });
}

function allowRequest(ip) {
  const now = Date.now();
  const recent = (requestsByIp.get(ip) || []).filter((time) => now - time < RATE_WINDOW_MS);
  if (recent.length >= RATE_LIMIT) return false;
  recent.push(now);
  requestsByIp.set(ip, recent);
  return true;
}

function parseModelJson(content) {
  const cleaned = content.trim().replace(/^```json\s*|^```|```$/gim, "");
  return JSON.parse(cleaned);
}

function text(value, fallback) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

function normalizeInsight(value) {
  return {
    oneLiner: text(value.oneLiner, "README 未提供足够信息以生成项目概述。"),
    capabilities: Array.isArray(value.capabilities)
      ? value.capabilities.filter((item) => typeof item === "string" && item.trim()).slice(0, 5)
      : [],
    audience: text(value.audience, "希望快速了解该开源项目的开发者。"),
    strengths: text(value.strengths, "请结合 README 中的公开信息理解项目特点。"),
    limitations: text(value.limitations, "README 未覆盖的能力、兼容性和部署条件需要进一步确认。"),
    score: Math.max(1, Math.min(10, Number(value.score) || 5)),
    evidence: text(value.evidence, "解读仅依据本次提交的 README 与仓库公开元数据生成。"),
    readmeHighlights: Array.isArray(value.readmeHighlights)
      ? value.readmeHighlights.filter((item) => typeof item === "string" && item.trim()).slice(0, 5)
      : [],
    modelVersion: `${model}-zh-insight-v2`,
  };
}

async function generateInsight(input) {
  const readme = input.readme.slice(0, 60_000);
  const prompt = `请基于以下公开 GitHub 仓库元数据和 README，用简体中文生成帮助开发者快速理解项目的解读。不要让读者回到 README 或原始仓库查阅；直接说明项目做什么、核心能力、适合谁、优势、限制、学习优先级与依据。不得臆造 README 中不存在的功能。只返回 JSON，不要 Markdown。

JSON 格式：
{
  "oneLiner": "一句中文项目概述",
  "capabilities": ["中文能力 1", "中文能力 2"],
  "audience": "中文适用人群说明",
  "strengths": "中文公开证据支持的优势",
  "limitations": "中文限制、前提或风险",
  "score": 1,
  "evidence": "说明解读依据了 README 中哪些具体内容"
}

仓库：${input.repository}
仓库链接：${input.repositoryUrl}
主要语言：${input.language || "未标注"}
公开话题：${Array.isArray(input.topics) ? input.topics.join(", ") : "无"}
README：
${readme}`;

  const upstream = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      temperature: 0.2,
      messages: [
        { role: "system", content: "你是一名严谨的中文开源项目技术分析助手。" },
        { role: "user", content: prompt },
      ],
    }),
  });
  if (!upstream.ok) throw new Error(`upstream_${upstream.status}`);
  const payload = await upstream.json();
  return normalizeInsight(parseModelJson(payload.choices?.[0]?.message?.content || "{}"));
}

http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    return json(response, 200, { ok: true, model });
  }
  if (request.method !== "POST" || request.url !== "/v1/insights") {
    return json(response, 404, { error: "not_found" });
  }
  const ip = request.socket.remoteAddress || "unknown";
  if (!allowRequest(ip)) return json(response, 429, { error: "rate_limited" });

  try {
    const input = await readJson(request);
    if (!input?.repository || !input?.repositoryUrl || !input?.readme) {
      return json(response, 400, { error: "repository, repositoryUrl and readme are required" });
    }
    const key = createHash("sha256")
      .update(`${INSIGHT_SCHEMA_VERSION}\n${input.repository}\n${input.readme}`)
      .digest("hex");
    const cached = cache.get(key);
    if (cached && Date.now() - cached.createdAt < CACHE_TTL_MS) {
      return json(response, 200, cached.value);
    }
    const value = await generateInsight(input);
    cache.set(key, { value, createdAt: Date.now() });
    return json(response, 200, value);
  } catch (error) {
    console.error("insight generation failed", error.message);
    return json(response, 502, { error: "insight_unavailable" });
  }
}).listen(port, () => {
  console.log(`DevPulse insight gateway listening on :${port}`);
});
