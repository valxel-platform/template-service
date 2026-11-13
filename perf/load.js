// Staging load test (k6): the product's expected peak, scaled to staging's size
// (TARGET_RPS = peak_rps x staging_load_factor, passed in by the Valxel SDLC), for three minutes
// after a warm-up. perf/compare.py judges it against the budget and the last release's run.
// Reads and writes in the API's usual mix; writes use synthetic records and an idempotency key.
import http from "k6/http";
import { check } from "k6";

const BASE = __ENV.BASE_URL;
const RATE = Math.max(1, Math.round(Number(__ENV.TARGET_RPS || 5)));
const WRITES = Number(__ENV.WRITE_SHARE || 0.2);
const params = { headers: { Authorization: `Bearer ${__ENV.API_TOKEN}`, "Content-Type": "application/json" } };

export const options = {
  scenarios: {
    warmup: { executor: "constant-arrival-rate", rate: Math.max(1, Math.round(RATE / 2)), timeUnit: "1s",
              duration: "30s", preAllocatedVUs: 10, maxVUs: 200 },
    load: { executor: "constant-arrival-rate", rate: RATE, timeUnit: "1s", duration: __ENV.DURATION || "3m",
            startTime: "30s", preAllocatedVUs: 10, maxVUs: 200 },
  },
  summaryTrendStats: ["avg", "p(95)", "p(99)", "max"],
  thresholds: { "http_req_duration{scenario:load}": ["max>=0"], "http_req_failed{scenario:load}": ["rate>=0"],
                "http_reqs{scenario:load}": ["count>=0"] },
};

export default function () {
  if (Math.random() < WRITES) {
    const id = `perf-${__VU}-${__ITER}`;
    const res = http.put(`${BASE}/api/v1/greetings/${id}`, JSON.stringify({ name: "Perf Test", message: "load" }),
      { headers: { ...params.headers, "Idempotency-Key": id } });
    check(res, { "write accepted": (r) => r.status === 202 });
  } else {
    const res = http.get(`${BASE}/api/v1/greetings?name=perf`, params);
    check(res, { "read ok": (r) => r.status === 200 });
  }
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY || "summary.json"]: JSON.stringify(data) };
}
