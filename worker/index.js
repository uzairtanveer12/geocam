// GeoCam report receiver (Cloudflare Worker)
// Receives an anonymous test report from the app and saves it as a text file in
// the GitHub repo folder  reports/YYYY-MM-DD/  . The GitHub token lives only in
// Cloudflare (as a secret), never in the app or in the repository.
//
// Settings needed in Cloudflare (Worker > Settings > Variables and Secrets):
//   GH_REPO   (text)    example: uzairtanveer12/geocam
//   GH_BRANCH (text)    example: main
//   GH_TOKEN  (secret)  fine-grained GitHub token, this repository only, Contents: Read and write
// Optional: a KV namespace bound with the variable name  RL  (limits uploads per IP per hour)

const MAX_BODY = 30000;      // bytes
const MAX_REPORT = 25000;    // characters
const PER_HOUR = 10;         // uploads per IP per hour (needs the RL KV binding)

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Max-Age": "86400",
};

function scrub(t) {
  return String(t)
    .replace(/-?\d{1,3}\.\d{4,}/g, "<num>")          // coordinates
    .replace(/[\w.+-]+@[\w.-]+\.\w+/g, "<email>")     // emails
    .replace(/key=[^\s&]+/gi, "key=<hidden>");        // API keys in urls
}

function safe(s, n) {
  return String(s || "unknown").replace(/[^A-Za-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, n) || "unknown";
}

function toBase64(text) {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 8192) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
  }
  return btoa(bin);
}

async function sha(text) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].slice(0, 8).map((b) => b.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(request, env) {
    const json = (o, status = 200) =>
      new Response(JSON.stringify(o), { status, headers: { ...CORS, "Content-Type": "application/json" } });

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/report") return json({ ok: false, error: "not found" }, 404);
    if (!env.GH_TOKEN || !env.GH_REPO) return json({ ok: false, error: "server is not configured" }, 500);

    const len = Number(request.headers.get("content-length") || 0);
    if (len > MAX_BODY) return json({ ok: false, error: "report too large" }, 413);

    let body;
    try {
      body = await request.json();
    } catch (e) {
      return json({ ok: false, error: "invalid json" }, 400);
    }
    let text = String((body && body.report) || "");
    if (text.length < 200 || text.length > MAX_REPORT || !text.startsWith("GEOCAM TEST REPORT")) {
      return json({ ok: false, error: "not a GeoCam report" }, 400);
    }
    text = scrub(text);

    if (env.RL) {
      const ip = request.headers.get("CF-Connecting-IP") || "unknown";
      const key = "rl:" + (await sha(ip)) + ":" + Math.floor(Date.now() / 3600000);
      const n = Number((await env.RL.get(key)) || 0);
      if (n >= PER_HOUR) return json({ ok: false, error: "too many reports, try again later" }, 429);
      await env.RL.put(key, String(n + 1), { expirationTtl: 7200 });
    }

    const meta = (body && body.meta) || {};
    const d = new Date();
    const day = d.toISOString().slice(0, 10);
    const stamp = d.toISOString().replace(/[-:T]/g, "").slice(0, 14);
    const name = safe(meta.model, 28) + "_A" + safe(meta.android, 6) + "_" + stamp + "_" + crypto.randomUUID().slice(0, 6) + ".txt";
    const path = "reports/" + day + "/" + name;

    const api = (env.GH_API || "https://api.github.com") + "/repos/" + env.GH_REPO + "/contents/" + path;
    const res = await fetch(api, {
      method: "PUT",
      headers: {
        Authorization: "Bearer " + env.GH_TOKEN,
        "User-Agent": "geocam-report-worker",
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: "Add test report " + safe(meta.model, 28) + " Android " + safe(meta.android, 6),
        content: toBase64(text),
        branch: env.GH_BRANCH || "main",
      }),
    });
    if (!res.ok) {
      const detail = (await res.text()).slice(0, 200);
      return json({ ok: false, error: "GitHub error " + res.status, detail }, 502);
    }
    return json({ ok: true, path });
  },
};
