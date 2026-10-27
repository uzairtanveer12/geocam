# GeoCam report receiver

A tiny Cloudflare Worker. The app sends an anonymous test report to it, and it saves the report as a text file in `reports/` in this repository. The GitHub token is stored only in Cloudflare, never in the app.

See the steps in the project chat or follow `index.js` header comments:

1. Create a fine-grained GitHub token: this repository only, permission "Contents: Read and write".
2. Cloudflare dashboard > Workers & Pages > Create > Worker > paste `index.js` > Deploy.
3. Worker > Settings > Variables and Secrets: add `GH_REPO` (text), `GH_BRANCH` (text) and `GH_TOKEN` (secret).
4. Optional: create a KV namespace and bind it with the variable name `RL` to limit uploads per IP.
5. Put the Worker address (ending in `/report`) into `REPORT_URL` near the top of `www/index.html`.
