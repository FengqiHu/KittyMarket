# 本机商品浏览并发压测（2026-10-17）

买家首页会读取热门商品和完整商品列表；商品详情页读取指定商品。测试在本机 Spring Boot 8080 端口进行，由同一台机器使用 HTTP 长连接持续发出只读 GET 请求。热门商品和列表请求校验 HTTP 200 及响应中的 `page=/buyer`；详情请求校验 HTTP 200。

判定条件：错误率不超过 1%，P95 响应时间不超过 1000 毫秒，每个并发工作线程至少完成 2 次请求。重复测试时，所有轮次都必须通过。这里的“并发”指同时执行的 API 请求，不等于已验证的浏览器用户数。

| 接口 | 响应内容 | 并发请求 | 每轮时长 | P95 响应时间 | 错误 | 结论 |
| --- | --- | ---: | --- | --- | ---: | --- |
| `GET /good/buyer-all-good-list-control` | 5026 件商品，约 3.16 MB | 40 | 10 秒 × 2 | 770 / 775 毫秒 | 0 / 0 | 两轮通过 |
| 同上 | 同上 | 48 | 10 秒 × 2 | 998 / 929 毫秒 | 0 / 0 | 两轮通过，已接近阈值 |
| 同上 | 同上 | 50 | 10 秒 × 2 | 1026 / 980 毫秒 | 0 / 0 | 一轮超过阈值 |
| 同上 | 同上 | 64 | 4 秒 | 1238 毫秒 | 0 | 未通过 |
| `GET /good/hot-goods` | 8 件商品，约 4.7 KB | 128 | 4 秒 | 24 毫秒 | 0 | 通过；测试上限为 128 |
| `GET /good/details/1` | 1 件商品，约 340 B | 128 | 4 秒 | 18 毫秒 | 0 | 通过；测试上限为 128 |

**结论：在本机及上述标准下，完整商品列表已验证 48 个并发请求通过两轮测试，但其中一轮 P95 达 998 毫秒，余量很小。规划持续负载时建议以已测得更有余量的 40 并发档位为参考。** 50 并发出现一轮未通过，因此不能把 50 当作稳定容量。完整列表在 40～50 并发时吞吐量约为每秒 57～59 次请求；一次请求传输约 3.16 MB。热门商品和详情接口在已测的 128 并发内没有达到同样的瓶颈。

复现命令：

```sh
python3 scripts/benchmark_concurrency.py --url 'http://127.0.0.1:8080/good/buyer-all-good-list-control' --expected-page /buyer --start-concurrency 48 --max-concurrency 48 --duration 10 --repeats 2 --timeout 10
python3 scripts/benchmark_concurrency.py --url 'http://127.0.0.1:8080/good/hot-goods' --expected-page /buyer --max-concurrency 128 --duration 4 --timeout 10
python3 scripts/benchmark_concurrency.py --url 'http://127.0.0.1:8080/good/details/1' --expected-page '' --max-concurrency 128 --duration 4 --timeout 10
```

测试只覆盖各接口的独立读取，不含同时打开页面时的资源加载、图片传输、浏览器渲染、购物会话或公网网络延迟。并发检索的 160 请求结果属于另一个接口，不能直接套用到完整商品列表浏览。
