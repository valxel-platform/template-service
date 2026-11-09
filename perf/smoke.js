// Performance smoke test for pull requests (k6). The workflow runs it against the image built from
// main, then against the PR's image, on the same runner, and perf/compare.py judges the PR against
// main and the budget. A steady arrival rate after a warm-up (the JVM compiles hot code first):
// only the "load" scenario is measured. Replace the request with your API's hot paths.
import http from "k6/http";
import { check } from "k6";

const BASE = __ENV.BASE_URL || "http://127.0.0.1:8080";
const RATE = Number(__ENV.RATE || 30);

export const options = {
  scenarios: {
    warmup: { executor: "constant-arrival-rate", rate: RATE, timeUnit: "1s", duration: "20s", preAllocatedVUs: 20, maxVUs: 100 },
    load: { executor: "constant-arrival-rate", rate: RATE, timeUnit: "1s", duration: __ENV.DURATION || "60s",
            startTime: "20s", preAllocatedVUs: 20, maxVUs: 100 },
  },
  summaryTrendStats: ["avg", "p(95)", "p(99)", "max"],
  // Sub-metrics for the measured scenario only (always true; compare.py decides).
  thresholds: { "http_req_duration{scenario:load}": ["max>=0"], "http_req_failed{scenario:load}": ["rate>=0"],
                "http_reqs{scenario:load}": ["count>=0"] },
};

export default function () {
  const res = http.get(`${BASE}/api/v1/greetings?name=perf`);
  check(res, { "status is 200": (r) => r.status === 200 });
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY || "summary.json"]: JSON.stringify(data) };
}
