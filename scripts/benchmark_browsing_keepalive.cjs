#!/usr/bin/env node
// Usage: node scripts/benchmark_browsing_keepalive.cjs 5120 10 [URL]
const http = require('node:http');
const { performance } = require('node:perf_hooks');

const concurrency = Number(process.argv[2]);
const durationMs = Number(process.argv[3]) * 1000;
const target = new URL(process.argv[4] || 'http://127.0.0.1:8080/good/buyer-goods-page?page=1&size=8');
if (!Number.isInteger(concurrency) || concurrency < 1 || !Number.isFinite(durationMs) || durationMs <= 0) {
  process.stderr.write('Usage: node scripts/benchmark_browsing_keepalive.cjs <concurrency> <seconds> [URL]\n');
  process.exit(2);
}

const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency });
const makeConnection = agent.createConnection.bind(agent);
let connections = 0;
agent.createConnection = (...args) => {
  connections++;
  return makeConnection(...args);
};
const latencies = [];
const problems = new Map();
let requests = 0;
let errors = 0;
let active = 0;
let peak = 0;
const started = performance.now();
const deadline = started + durationMs;

function requestOnce() {
  return new Promise(resolve => {
    const began = performance.now();
    active++;
    peak = Math.max(peak, active);
    let finished = false;
    function finish(problem) {
      if (finished) return;
      finished = true;
      requests++;
      active--;
      latencies.push(performance.now() - began);
      if (problem) {
        errors++;
        problems.set(problem, (problems.get(problem) || 0) + 1);
      }
      resolve();
    }
    const req = http.get(target, { agent, headers: { Accept: 'application/json' } }, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('error', error => finish(error.code || error.name));
      res.on('aborted', () => finish('aborted'));
      res.on('end', () => {
        if (res.statusCode !== 200) return finish(`HTTP ${res.statusCode}`);
        try {
          const data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
          finish(data.page === '/buyer' && Array.isArray(data.data?.items) ? null : 'wrong response');
        } catch (_) {
          finish('invalid JSON');
        }
      });
    });
    req.setTimeout(10000, () => req.destroy(new Error('timeout')));
    req.on('error', error => finish(error.code || error.message));
  });
}

async function worker() {
  while (performance.now() < deadline) await requestOnce();
}

Promise.all(Array.from({ length: concurrency }, worker)).then(() => {
  agent.destroy();
  latencies.sort((a, b) => a - b);
  const p95 = latencies[Math.ceil(latencies.length * 0.95) - 1] || 0;
  const elapsed = (performance.now() - started) / 1000;
  const passed = peak === concurrency && requests >= concurrency * 2
    && errors / requests <= 0.01 && p95 <= 1000;
  process.stdout.write(JSON.stringify({ concurrency, peak, requests, errors, errorRate: errors / requests,
    p95Ms: p95, requestsPerSecond: requests / elapsed, elapsedSeconds: elapsed,
    connections, problems: Object.fromEntries(problems), passed }) + '\n');
  process.exitCode = passed ? 0 : 1;
});
