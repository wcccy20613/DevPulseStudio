import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const outputDirectory = resolve("static");
const catalogPath = resolve(outputDirectory, "catalog.json");
const dailyPath = resolve(outputDirectory, "daily.json");
const githubToken = process.env.GITHUB_TOKEN;
const aiBaseUrl = process.env.AI_API_BASE_URL?.trim();
const aiApiKey = process.env.AI_API_KEY?.trim();
const aiModel = process.env.AI_MODEL?.trim() || "";
const insightLimit = Math.max(0, Math.min(Number(process.env.AI_INSIGHT_LIMIT || 30), 60));

const searchQueries = [
  "topic:artificial-intelligence stars:>50",
  "topic:llm stars:>20",
  "topic:agent stars:>20",
  "topic:model-context-protocol stars:>10",
  "topic:retrieval-augmented-generation stars:>10",
  "topic:generative-ai stars:>20",
];

const readJson = async (path, fallback) => {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch {
    return fallback;
  }
};

const writeJson = async (path, value) => writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");

const github = async (path) => {
  const response = await fetch(`https://api.github.com${path}`, {
    headers: {
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(githubToken ? { Authorization: `Bearer ${githubToken}` } : {}),
    },
  });
  if (!response.ok) throw new Error(`GitHub ${response.status} for ${path}`);
  return response.json();
};

const classify = (name, description = "", topics = [], language = "") => {
  const text = [name, description, language, ...topics].join(" ").toLowerCase();
  if (/(deepfake|face-swap|face swap)/.test(text)) return "IMAGE";
  if (/(model-context-protocol|\bmcp\b)/.test(text)) return "MCP";
  if (/(retrieval-augmented|vector database|\brag\b)/.test(text)) return "RAG";
  if (/(autonomous|\bagent\b)/.test(text)) return "AGENT";
  if (/(stable-diffusion|image-generation|comfyui|text-to-image)/.test(text)) return "IMAGE";
  if (/(video-generation|text-to-video)/.test(text)) return "VIDEO";
  if (/(copilot|code-generation|coding-assistant)/.test(text)) return "CODING";
  if (/(android|kotlin)/.test(text)) return "ANDROID";
  if (/(frontend|\bweb\b|react)/.test(text)) return "WEB";
  return "LLM";
};

const toCatalogItem = (repository, previous) => {
  const topics = repository.topics || [];
  const summary = repository.description || "";
  const stars = repository.stargazers_count || 0;
  const oldStars = previous?.stars ?? stars;
  return {
    id: repository.id,
    title: repository.full_name,
    summary,
    author: repository.owner.login,
    language: repository.language || "未标注",
    stars,
    url: repository.html_url,
    avatarUrl: repository.owner.avatar_url,
    forks: repository.forks_count || 0,
    openIssues: repository.open_issues_count || 0,
    license: repository.license?.spdx_id || "未声明",
    updatedAt: repository.updated_at || "",
    createdAt: repository.created_at || "",
    topics,
    track: classify(repository.full_name, summary, topics, repository.language || ""),
    archived: Boolean(repository.archived),
    isRisky: /(deepfake|deep-fake|face-swap|faceswap|换脸)/i.test([repository.full_name, summary, ...topics].join(" ")),
    starDelta24h: Math.max(0, stars - oldStars),
    latestRelease: previous?.latestRelease || null,
    aiBrief: previous?.updatedAt === repository.updated_at ? previous.aiBrief || null : null,
    aiBriefUpdatedAt: previous?.updatedAt === repository.updated_at ? previous.aiBriefUpdatedAt || null : null,
  };
};

const fetchReadme = async (fullName) => {
  const [owner, repository] = fullName.split("/", 2);
  if (!owner || !repository) return "";
  try {
    const payload = await github(`/repos/${owner}/${repository}/readme`);
    if (!payload.content || payload.encoding !== "base64") return "";
    return Buffer.from(payload.content, "base64").toString("utf8").slice(0, 30_000);
  } catch {
    return "";
  }
};

const parseJson = (content) => {
  const unwrapped = content.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try { return JSON.parse(unwrapped); } catch { return null; }
};

const createInsight = async (item, readme) => {
  if (!aiBaseUrl || !aiApiKey || !aiModel || !readme) return null;
  const prompt = `你是 DevPulse Studio 的开源项目分析器。仅使用提供的 README 与元数据；证据不足必须明确说明，不能声称代码审计、安全或生产保证。返回纯 JSON：oneLiner(string), capabilities(string[]), audience(string), strengths(string), limitations(string), score(1-10 integer), evidence(string)。\n仓库：${item.title}\n语言：${item.language}\nTopics：${item.topics.join(", ")}\nREADME：\n${readme}`;
  try {
    const response = await fetch(`${aiBaseUrl.replace(/\/+$/, "")}/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${aiApiKey}` },
      body: JSON.stringify({ model: aiModel, temperature: 0.2, messages: [{ role: "user", content: prompt }] }),
    });
    if (!response.ok) throw new Error(`AI provider ${response.status}`);
    const payload = await response.json();
    const raw = parseJson(payload.choices?.[0]?.message?.content || "");
    if (!raw || typeof raw.oneLiner !== "string") return null;
    return {
      oneLiner: raw.oneLiner.slice(0, 400),
      capabilities: Array.isArray(raw.capabilities) ? raw.capabilities.filter((item) => typeof item === "string").slice(0, 5) : [],
      audience: typeof raw.audience === "string" ? raw.audience.slice(0, 300) : "请结合 README 判断适配人群。",
      strengths: typeof raw.strengths === "string" ? raw.strengths.slice(0, 400) : "请以项目公开证据为准。",
      limitations: typeof raw.limitations === "string" ? raw.limitations.slice(0, 400) : "模型结论不等同于代码审计或生产保证。",
      score: Math.max(1, Math.min(10, Number(raw.score) || 5)),
      evidence: typeof raw.evidence === "string" ? raw.evidence.slice(0, 500) : "基于公开 README 与仓库元数据生成。",
      sourceLabel: `AI 定时解读 · ${aiModel}`,
      readmeHighlights: [],
      previewImageUrl: null,
    };
  } catch (error) {
    console.warn(`AI insight skipped for ${item.title}: ${error.message}`);
    return null;
  }
};

const latestRelease = async (item) => {
  const [owner, repository] = item.title.split("/", 2);
  try {
    const release = await github(`/repos/${owner}/${repository}/releases/latest`);
    return { tag: release.tag_name || "", publishedAt: release.published_at || "", url: release.html_url || "" };
  } catch {
    return item.latestRelease || null;
  }
};

await mkdir(outputDirectory, { recursive: true });
const previousCatalog = await readJson(catalogPath, { items: [] });
const previousById = new Map((previousCatalog.items || []).map((item) => [item.id, item]));
const repositories = [];
for (const query of searchQueries) {
  const result = await github(`/search/repositories?q=${encodeURIComponent(query)}&sort=stars&order=desc&per_page=30`);
  repositories.push(...(result.items || []));
}

const items = [...new Map(repositories.map((repository) => [repository.id, repository])).values()]
  .sort((left, right) => right.stargazers_count - left.stargazers_count)
  .slice(0, 80)
  .map((repository) => toCatalogItem(repository, previousById.get(repository.id)));

for (const item of items.slice(0, 50)) item.latestRelease = await latestRelease(item);
for (const item of items.slice(0, insightLimit)) {
  if (item.aiBrief) continue;
  const insight = await createInsight(item, await fetchReadme(item.title));
  if (insight) {
    item.aiBrief = insight;
    item.aiBriefUpdatedAt = new Date().toISOString();
  }
}

const generatedAt = new Date().toISOString();
const catalog = { schemaVersion: 1, generatedAt, items };
const dayAgo = Date.now() - 24 * 60 * 60 * 1000;
const daily = {
  schemaVersion: 1,
  generatedAt,
  popular: [...items].sort((left, right) => right.stars - left.stars).slice(0, 10).map((item) => item.id),
  newProjects: items.filter((item) => Date.parse(item.createdAt) >= dayAgo).sort((left, right) => right.stars - left.stars).slice(0, 10).map((item) => item.id),
  growing: items.filter((item) => item.starDelta24h > 0).sort((left, right) => right.starDelta24h - left.starDelta24h).slice(0, 10).map((item) => item.id),
  releases: items.filter((item) => item.latestRelease?.publishedAt && Date.parse(item.latestRelease.publishedAt) >= dayAgo).slice(0, 10).map((item) => item.id),
};

await writeJson(catalogPath, catalog);
await writeJson(dailyPath, daily);
console.log(`Generated ${items.length} repositories at ${generatedAt}`);
