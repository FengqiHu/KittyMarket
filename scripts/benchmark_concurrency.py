#!/usr/bin/env python3
"""Find a threshold-based concurrency limit for a read-only KittyMarket GET endpoint."""

from __future__ import annotations

import argparse
import concurrent.futures
import http.client
import json
import math
import threading
import time
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import quote, urlsplit


DEFAULT_URL = (
    "http://127.0.0.1:8080/good/rag-search"
    "?query=%E5%B9%BC%E7%8C%AB%E9%B8%A1%E8%82%89&kind=%E7%8C%AB%E5%92%AA%E4%B8%BB%E7%B2%AE"
)


@dataclass
class Stage:
    concurrency: int
    peak_in_flight: int
    requests: int
    errors: int
    error_rate: float
    p50_ms: float
    p95_ms: float
    p99_ms: float
    requests_per_second: float
    elapsed_seconds: float
    passed: bool
    error_examples: dict[str, int]


def percentile(values: list[float], percentage: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    position = math.ceil(len(ordered) * percentage / 100) - 1
    return ordered[max(0, position)]


def connection_for(parts, timeout: float):
    factory = http.client.HTTPSConnection if parts.scheme == "https" else http.client.HTTPConnection
    return factory(parts.hostname, parts.port, timeout=timeout)


def request_once(connection, target: str, expected_page: str | None) -> str | None:
    connection.request("GET", target, headers={"Accept": "application/json"})
    response = connection.getresponse()
    body = response.read()
    if response.status != 200:
        return f"HTTP {response.status}"
    if expected_page is not None:
        try:
            payload = json.loads(body)
        except (UnicodeDecodeError, json.JSONDecodeError):
            return "invalid JSON"
        if not isinstance(payload, dict) or payload.get("page") != expected_page:
            return f"application response: {str(payload.get('page'))[:40]}" if isinstance(payload, dict) else "unexpected JSON"
    return None


def run_stage(args, parts, target: str, concurrency: int) -> Stage:
    clock = [0.0, 0.0]

    def start_stage():
        clock[0] = time.perf_counter()
        clock[1] = clock[0] + args.duration

    barrier = threading.Barrier(concurrency + 1, action=start_stage, timeout=15)
    lock = threading.Lock()
    active = 0
    peak = 0

    def worker():
        nonlocal active, peak
        latencies = []
        errors = Counter()
        connection = None
        barrier.wait()
        while time.perf_counter() < clock[1]:
            started = time.perf_counter()
            with lock:
                active += 1
                peak = max(peak, active)
            try:
                if connection is None:
                    connection = connection_for(parts, args.timeout)
                problem = request_once(connection, target, args.expected_page)
                if problem:
                    errors[problem] += 1
            except (OSError, http.client.HTTPException, TimeoutError) as exc:
                errors[type(exc).__name__] += 1
                if connection is not None:
                    connection.close()
                    connection = None
            finally:
                latencies.append((time.perf_counter() - started) * 1000)
                with lock:
                    active -= 1
        if connection is not None:
            connection.close()
        return latencies, errors

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker) for _ in range(concurrency)]
        barrier.wait()
        outcomes = [future.result() for future in futures]
        elapsed = time.perf_counter() - clock[0]

    latencies = [latency for worker_latencies, _ in outcomes for latency in worker_latencies]
    errors = Counter()
    for _, worker_errors in outcomes:
        errors.update(worker_errors)
    requests = len(latencies)
    error_count = errors.total()
    error_rate = error_count / requests if requests else 1.0
    p95 = percentile(latencies, 95)
    passed = (
        requests >= max(20, concurrency * 2)
        and error_rate <= args.max_error_rate
        and p95 <= args.p95_limit_ms
    )
    return Stage(
        concurrency, peak, requests, error_count, error_rate,
        percentile(latencies, 50), p95, percentile(latencies, 99),
        requests / elapsed if elapsed else 0.0, elapsed, passed,
        dict(errors.most_common(3)),
    )


def print_stage(stage: Stage) -> None:
    outcome = "PASS" if stage.passed else "FAIL"
    print(
        f"{stage.concurrency:>4} concurrent | peak {stage.peak_in_flight:>4} | "
        f"{stage.requests:>5} req | {stage.requests_per_second:>7.1f} req/s | "
        f"p50 {stage.p50_ms:>7.1f} ms | p95 {stage.p95_ms:>7.1f} ms | "
        f"errors {stage.errors:>4} ({stage.error_rate:.1%}) | {outcome}",
        flush=True,
    )
    if stage.error_examples:
        print(f"     errors: {stage.error_examples}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=DEFAULT_URL, help="read-only HTTP(S) GET URL")
    parser.add_argument("--start-concurrency", type=int, default=1, help="first level to test")
    parser.add_argument("--max-concurrency", type=int, default=128)
    parser.add_argument("--repeats", type=int, default=1, help="rounds required to pass at each level")
    parser.add_argument("--duration", type=float, default=4.0, help="seconds per stage")
    parser.add_argument("--timeout", type=float, default=5.0, help="seconds per request")
    parser.add_argument("--p95-limit-ms", type=float, default=1000.0)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    parser.add_argument("--expected-page", default="success", help="expected JSON page field; empty disables check")
    parser.add_argument("--json-out", type=Path, help="write the full stage results as JSON")
    args = parser.parse_args()

    parts = urlsplit(args.url)
    if parts.scheme not in ("http", "https") or not parts.hostname:
        parser.error("--url must be an HTTP(S) URL")
    if not 1 <= args.max_concurrency <= 1024:
        parser.error("--max-concurrency must be between 1 and 1024")
    if not 1 <= args.start_concurrency <= args.max_concurrency:
        parser.error("--start-concurrency must be between 1 and --max-concurrency")
    if not 1 <= args.repeats <= 10:
        parser.error("--repeats must be between 1 and 10")
    if args.duration <= 0 or args.timeout <= 0 or args.p95_limit_ms <= 0:
        parser.error("duration, timeout and p95 limit must be positive")
    if not 0 <= args.max_error_rate <= 1:
        parser.error("--max-error-rate must be between 0 and 1")
    args.expected_page = args.expected_page or None
    target = parts.path or "/"
    if parts.query:
        target += "?" + parts.query
    target = quote(target, safe="/?&=%:@+$,;~-._!*'()")

    try:
        probe = connection_for(parts, args.timeout)
        problem = request_once(probe, target, args.expected_page)
        probe.close()
    except (OSError, http.client.HTTPException, TimeoutError) as exc:
        print(f"Preflight failed: {type(exc).__name__}: {exc}")
        return 2
    if problem:
        print(f"Preflight failed: {problem}")
        return 2

    print(
        f"Target: {args.url}\n"
        f"Pass: error rate <= {args.max_error_rate:.1%}, "
        f"p95 <= {args.p95_limit_ms:g} ms, at least 2 requests per worker, "
        f"{args.repeats} round(s) per level\n",
        flush=True,
    )
    stages: dict[int, list[Stage]] = {}

    def measure(concurrency: int) -> bool:
        rounds = []
        for number in range(1, args.repeats + 1):
            stage = run_stage(args, parts, target, concurrency)
            rounds.append(stage)
            if args.repeats > 1:
                print(f"  round {number}/{args.repeats}")
            print_stage(stage)
        stages[concurrency] = rounds
        return all(stage.passed for stage in rounds)

    passed = 0
    failed = None
    concurrency = args.start_concurrency
    try:
        while concurrency <= args.max_concurrency:
            if measure(concurrency):
                passed = concurrency
                if concurrency == args.max_concurrency:
                    break
                concurrency = min(concurrency * 2, args.max_concurrency)
            else:
                failed = concurrency
                break
        if failed is not None and passed > 0:
            while failed - passed > 1:
                midpoint = (passed + failed) // 2
                if measure(midpoint):
                    passed = midpoint
                else:
                    failed = midpoint
    except KeyboardInterrupt:
        print("\nInterrupted; partial results below.")
    except (threading.BrokenBarrierError, RuntimeError) as exc:
        print(f"\nLoad generator could not start {concurrency} workers: {exc}")
        failed = concurrency

    if passed == args.max_concurrency:
        verdict = f">= {passed} (configured test cap; raise --max-concurrency to probe further)"
    elif passed:
        verdict = str(passed)
    else:
        verdict = "none in the tested range under the chosen latency/error limits"
    print(f"\nEstimated maximum concurrent requests: {verdict}")
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps({
            "url": args.url,
            "criteria": {"p95_limit_ms": args.p95_limit_ms, "max_error_rate": args.max_error_rate,
                         "repeats": args.repeats, "duration_seconds": args.duration},
            "maximum_passing_concurrency": passed,
            "first_failing_concurrency": failed,
            "stages": [{"concurrency": level, "passed": all(stage.passed for stage in rounds),
                        "rounds": [asdict(stage) for stage in rounds]}
                       for level, rounds in sorted(stages.items())],
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Saved: {args.json_out}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
