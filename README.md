This is Kitty online shopping system.
Welcome to use!
If you encountered any bugs, please contact us immediately, thanks!

## Docker Compose 运行

使用 `maoliang-qianduan` 和 `maoliang-houduan`，其余目录是旧版 Servlet 项目。
运行环境统一由 Docker Compose 管理：Vue 静态站点及 Nginx、Spring Boot、MySQL、Redis、RabbitMQ。宿主机只需 Docker；构建过程会在容器中运行 npm 和 Maven。

首次启动：

```sh
cp .env.example .env
docker compose up -d --build
docker compose ps
```

前端访问 http://127.0.0.1:3000/，后端直连 http://127.0.0.1:8080/，RabbitMQ 管理界面访问 http://127.0.0.1:15672/。前端的 `/api` 由 Nginx 转发到容器内后端；MySQL、Redis 和 RabbitMQ 的服务端口只在 Compose 网络中开放，不占用宿主机的相应端口。各容器使用服务名互相连接。

接口默认启用令牌桶限流：登录用户按服务端会话中的用户 ID 共用额度，游客按客户端 IP 共用额度，修改请求里的 `userid`、换会话或换标签页不会重置同一身份的额度。所有请求先经过普通额度，写操作、AI/语义搜索/选粮、登录注册及找回密码再经过各自的额度。

| 请求类别 | 瞬时最多放行 | 持续恢复速度 |
| --- | --- | --- |
| 普通接口 | 20 次 | 每秒 1 次 |
| 收藏、购物车、下单等写操作 | 5 次 | 每 3 秒 1 次 |
| AI 对话、语义搜索及选粮 | 2 次 | 每 6 秒 1 次 |
| 登录、注册及找回密码（按 IP） | 5 次 | 每 12 秒 1 次 |

超限返回 HTTP `429`、`Retry-After` 秒数及 `data.code=RATE_LIMITED`，前端统一提示等待时间，不自动重试写操作。静态图片、脚本及 OPTIONS 预检不计入额度。Redis Lua 原子扣减在多个后端实例间共享额度；Redis 不可用时使用有容量上限的本机限流，故障期间额度按实例计算，恢复后重新使用共享额度。配置见 `.env.example` 的 `RATE_LIMIT_*`，`RATE_LIMIT_ENABLED=false` 可关闭。Compose 仅信任 `frontend` 容器覆盖写入的 `X-Real-IP`；直连后端不信任客户端自行传入的转发头。单独运行后端时默认使用连接来源 IP，如需使用其他反向代理，应将 `RATE_LIMIT_TRUSTED_PROXY` 设置为该代理的主机名或 IP。

首次创建 MySQL 数据卷时，容器会执行 `schema.sql` 和 `database/demo-data.sql`，创建业务表并导入演示数据；之后数据保存在 `mysql_data` 卷中，不会因重建容器丢失。商品上传图片保存在 `uploaded_images` 卷中，已有目录 `data/images` 以只读方式挂载给后端。启动前可在 `.env` 中填写 `DASHSCOPE_API_KEY`，留空时使用本地检索和规则推荐。`.env` 不提交到 Git；共享部署须修改默认密码。

停止服务使用 `docker compose down`；不要加 `-v`，除非明确要删除 MySQL、RabbitMQ 和上传图片的数据卷。更新代码后运行 `docker compose up -d --build`。查看日志使用 `docker compose logs -f backend`。

已有宿主机 MySQL 数据不会自动复制到容器。迁移时，先停止旧应用写入，备份原数据库，再只启动 Compose 数据库并导入备份：

```sh
mkdir -p .local
mysqldump -h 127.0.0.1 -u root -p --single-transaction --no-tablespaces maoliang > .local/maoliang-before-docker.sql
docker compose up -d mysql
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < .local/maoliang-before-docker.sql
docker compose up -d --build
```

检查行数、账号和商品图片后再切换应用。不要对已有数据库额外运行演示导入脚本，以免改变演示图片路径。宿主机旧 Java、Redis 和 MySQL 进程不属于这套 Compose 部署；确认容器后端已接管 `8080` 后，停止旧 Java 进程。其他本机 Redis 和 MySQL 实例不会被容器应用使用。

### 数据库

容器数据库名为 `maoliang`，字符集为 `utf8mb4`。需要手动检查时，在项目根目录执行：

```sh
docker compose exec mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

只在需要为一个已有数据库补充演示数据时手动执行：

```sh
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < database/demo-data.sql
```

`demo-data.sql` 包含买卖家账号、全部 26 件商品（主粮 10 件、零食 8 件、日用 8 件）、商品历史记录、演示订单及猫咪档案。已有数据库也执行这一份脚本：按账号、卖家及商品名补齐缺失数据，保留已有账号密码、商品价格、库存和订单，并更新演示商品及历史记录的图片路径。脚本可重复执行，不依赖固定的用户或商品 ID。商品归属演示卖家 `123`。演示描述及适用范围仅用于展示。

26 件演示商品各有一张通过内置 ImageGen 生成的独立商品图，保存在 `maoliang-qianduan/public/demo/products/`，使用 WebP 格式，浏览时无需外网。商品、文件与完整生成提示词的对应关系见 `docs/product-images.json`。导入后按下文清除 Redis 热门缓存，即可立即显示更新。

后端启动时也会自动创建缺失的业务表，保留已有表和数据。
旧版 `Setup/Setup.java` 包含删表操作，不用于当前 MySQL 初始化。

| 演示角色 | 用户名 | 密码 |
| --- | --- | --- |
| 买家 | 111 | 111 |
| 卖家 | 123 | 123 |

容器凭据从项目根目录的 `.env` 读取，不会加载旧的 `maoliang-houduan/application-local.properties`。

### Redis 与热门商品

Redis 随 `docker compose up` 自动启动，内存上限 128MB，使用 LRU 淘汰缓存，不持久化业务数据。

### RabbitMQ 与 RAG 索引更新

商品记录是 RAG 使用的检索文档。RabbitMQ 随 `docker compose up` 自动启动，容器后端默认启用 `RAG_MQ_ENABLED=true`。

默认管理账号和密码为 `kittymarket` / `kittymarket-local`；共享环境应在 `.env` 中修改。未启用 MQ 时保留原有按商品内容检查索引的方式。

商品新增、修改、删除，以及下单、取消订单引起的库存变化，会在同一 MySQL 事务中写入 `rag_catalog_outbox`。发布者收到 RabbitMQ 确认后删除待发送记录；连接失败时保留记录并在下一轮重试。消费者按数据库最新商品状态刷新 LangChain4j 的内存向量索引；处理失败重试 3 次后进入 `kittymarket.catalog.index.dead.v1` 死信队列，排查后可重新投递。商品搜索仍会读取实时数据库并校验索引内容，因此队列暂时滞后时不会返回已下架或售罄商品。没有 Qwen API Key 时，消息仍会被消费，检索继续使用本地模式。

RabbitMQ 队列在首次连接时由后端声明；仅在 `RAG_MQ_ENABLED=true` 时连接。该索引位于单个后端进程内，多进程部署时每个进程仍会在检索时根据数据库内容校验并补建索引。

### 并发压测

压测客户端也在 Compose 网络中的容器运行。只读商品检索示例：

```sh
docker compose run --rm --no-deps search-benchmark --url 'http://backend:8080/good/rag-search?query=幼猫鸡肉&kind=猫咪主粮' --max-concurrency 128 --duration 4
docker compose run --rm --no-deps benchmark 256 10 'http://backend:8080/good/buyer-goods-page?page=1&size=8'
```

检索脚本从 1 个并发请求开始逐级加压，再在通过与失败的档位间查找边界；已测过低档位时可用 `--start-concurrency` 继续向上探测。默认要求错误率不超过 1%、P95 响应时间不超过 1000 毫秒；可用 `--p95-limit-ms`、`--max-error-rate` 调整标准，建议用 `--repeats 2 --duration 10` 复核边界。`--url` 可测试其他只读 GET 接口，`--expected-page ''` 可关闭业务响应校验。输出的是当前机器、数据量、接口和判定标准下的估计值；若所有档位通过，只能确定上限至少达到 `--max-concurrency`。启用 Qwen 时，模型服务的配额、响应时间与调用费用也会计入结果。

先前直接运行本机 Java 后端时的测试环境与结果见 [`docs/concurrency-benchmark-2026-10-17.md`](docs/concurrency-benchmark-2026-10-17.md)；这些数值不代表 Compose 容器部署的容量，需要重新压测。

买家首页现在按每页 8 件从数据库读取商品，分类和页码在服务端处理，接口为 `GET /good/buyer-goods-page?category=全部&page=1&size=8`。`MLgood` 的 `(state, goodid)` 和 `(state, kind, goodid)` 索引用于在售商品计数及分类浏览，已有数据库在后端启动时会幂等补建。浏览接口改造与索引复测结果见 [`docs/browsing-optimization-2026-10-18.md`](docs/browsing-optimization-2026-10-18.md)。

商城首页的“热门商品”调用 `GET /good/hot-goods`，返回最多 8 件在售且有库存的商品。按未取消订单的商品数量、收藏人数、商品 ID 依次降序排列；暂无销量或收藏时展示最新商品。热门列表及商品信息以 JSON 缓存在 Redis，MySQL 负责真实价格和库存校验。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | 空 | Redis ACL 用户及密码 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `HOT_GOODS_CACHE_ENABLED` | `true` | 设为 `false` 时直接查 MySQL |
| `HOT_GOODS_CACHE_TTL` | `5m` | 缓存有效期，至少 `1ms` |

新增、删除、改价、收藏、下单及取消订单都会清除热门缓存；数据库事务中的操作在提交成功后清除，回滚时保留缓存。Redis 使用版本号和 Lua 脚本，防止并发查询在清除后回填旧数据。Redis 连接和命令超时均为 300ms，失败后热门查询在 10 秒内直接回源 MySQL；缓存故障不会导致下单失败。Redis 故障期间若清除失败，旧展示数据最多保留到原有 TTL 到期，下单仍以 MySQL 为准。

手动修改数据库或导入演示数据后，可等待 TTL 到期，或同时清除本项目热门缓存及版本标记：

```sh
docker compose exec redis redis-cli DEL 'kittymarket:{hot-goods}:v1' 'kittymarket:{hot-goods}:v1:generation'
```

`docker compose build backend frontend` 会在构建容器内完成 Maven 打包和 Vue 生产构建。集成测试会创建并清理数据，应在独立测试数据库运行。

### Qwen 与 RAG 检索

后端使用 LangChain4j 将在售商品记录构造成检索片段，以 Qwen 向量模型进行语义检索。商品搜索只返回商品结果，不再调用对话模型生成已从页面移除的检索说明；猫咪选粮仍可生成有商品依据的推荐说明。`GET /good/rag-search?query=...&kind=...&page=1&size=8` 用于买家商品检索，响应包含当前页的 `items` 和全部匹配数 `total`；`GET /cat/recommend/{catId}?batch=0` 根据当前登录用户指定的猫咪档案推荐在售主粮，每批最多 12 款，增加 `batch` 可依次查看下一批。推荐不计算喂食量；已标注的年龄、体重范围用于排除不匹配商品，未标注范围的商品仍可参与排序，品种只作为排序参考。未标注不表示已确认适用。

启动后端前设置以下环境变量。密钥应只配置在后端环境，不能写入前端代码或提交到 Git：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 空 | 阿里云百炼 API Key；留空时使用本地检索与规则推荐 |
| `DASHSCOPE_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 与 API Key 所在地域一致的 OpenAI 兼容接口地址；工作空间域名可在百炼控制台获取 |
| `QWEN_CHAT_MODEL` | `qwen-plus` | 生成猫咪选粮说明的模型 |
| `QWEN_EMBEDDING_MODEL` | `text-embedding-v4` | 商品与查询的向量模型 |
| `RAG_INDEX_CONCURRENCY` | `4` | 商品向量请求的并发数，限制在 1～8，每批最多 10 件 |
| `RAG_SEARCH_MIN_SCORE` | `0.85` | 搜索相关度最低分，范围 0～1；LangChain4j 分数等于 `(余弦相似度 + 1) / 2` |
| `RAG_SEARCH_SCORE_WINDOW` | `0.03` | 与最高相关度分数允许的差值，范围 0～1；过滤较弱结果 |

`QWEN_EMBEDDING_MODEL` 也支持 `tongyi-embedding-vision-plus-2026-03-06`。该模型自动使用百炼原生多模态向量接口，以文本独立向量方式处理商品文案与查询，默认 1152 维；`DASHSCOPE_BASE_URL` 仍填写原有兼容接口地址，后端自动转换向量接口路径。切换模型会建立独立的商品索引，首次需要重新计算向量，之后持久化复用。[百炼原生接口文档](https://help.aliyun.com/zh/model-studio/multimodal-embedding-api-reference)

有搜索词时，仅保留分数不低于 `max(RAG_SEARCH_MIN_SCORE, 最高分 - RAG_SEARCH_SCORE_WINDOW)` 的商品，按相关度排序后分页；`total` 为筛选后的命中数。没有命中时返回空列表，空搜索词仍可浏览全部商品。门槛基于当前 `tongyi-embedding-vision-plus-2026-03-06` 的真实目录样本校准，切换其他模型后可调整。猫咪档案推荐在已标注适用范围的商品上校验年龄、体重后排序，不使用搜索门槛，也不要求商品提供热量数据。

商品向量持久化到 MySQL 的 `rag_product_embedding` 表，并按接口地址、向量模型与商品文档哈希校验复用。重启后从数据库装载已有向量；仅新增或文案、分类、适用范围变化的商品需要重新调用向量模型，价格和正库存数量变化不触发向量化。首次全量初始化采用有限并发，每批完成即保存，失败后可接着处理缺失部分。同一查询的向量缓存最多 256 条，排序缓存最多 128 条，均保留 10 分钟，并合并相同查询的并发请求；翻页直接复用完整排序，商品展示数据和上架、库存状态仍实时读取。新的查询仍需可用的向量模型额度；查询缓存重启后清空。Qwen 调用不可用（包括额度耗尽）时会退回本地关键词检索或规则推荐，仍不会返回下架、无库存或不适用的推荐商品。配置密钥后，商品文案以及所选猫咪的档案信息会发送至所配置的百炼接口。

参考：[LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag/)、[阿里云百炼向量模型](https://help.aliyun.com/en/model-studio/embedding)。

### 个人管理与收货地址

登录后点击顶栏头像进入 `/account`，可查看历史订单、收藏，修改账号名称和密码，并管理多条收货地址。个人页与结算页共用地址簿，支持新增、编辑、删除和设置默认地址；结算默认选中默认地址，修改地址不会影响已提交订单。

账号与地址接口位于 `/account`，操作对象由登录会话确定。启动时自动扩展密码字段、添加账号名称唯一约束并创建 `MLaddress`；首次打开地址簿时导入原有有效地址，之后不会重复导入。新设置的密码使用 PBKDF2 哈希，原有密码可继续登录；修改账号名称会在同一事务中同步历史订单的账号关联。

### 界面与验证

前端使用适配 Vue 3 的 Element Plus，统一了登录注册、买家商城、购物车、收藏、猫咪档案和卖家后台的布局、表格、表单及操作反馈。商城使用顶部导航和商品分类，商家后台使用侧栏和紧凑表格；支持窄屏菜单，页面和组件按需加载。

```sh
docker compose build backend frontend
```

结算已接入 `/order/createorder-control`：校验登录和收货信息，在同一数据库事务中创建订单并扣减库存，成功后进入“我的订单”。从购物车结算会扣减购物车数量并保留收藏；发货前取消订单会恢复库存。后端启动时会自动为旧订单表补充收货人字段，保留原有数据。

猫咪档案支持新增、编辑和删除；商品检索与指定猫咪推荐支持 Qwen RAG，并可在未配置密钥时使用本地检索。

### Dify AI 智能客服（多分支专家工作流）

商城右下角提供悬浮 **AI 客服** 按钮，点击可开启对话或切换查看、继续、删除历史会话（持久化于 `MLcs_conversation` 与 `MLcs_message` 表）。项目内置 **12 节点多分支 Dify Chatflow DSL 文件** [`docs/dify-kittymarket-chatflow.yml`](docs/dify-kittymarket-chatflow.yml)，可在 Dify 控制台直接通过 **「导入 DSL 文件」** 一键创建完整工作流（包含：`开始` → `商城多维上下文组装 (Template)` → `五维意图智能路由 (Question Classifier)` → `订单操作与售后条件分支 (IF/ELSE)` → `6 大垂类专家 LLM 节点` → `多路专家回复聚合器 (Variable Aggregator)` → `回答`）。

支持六大核心业务场景：
- **偏好导购推荐（固定推荐 3 款商品）**：结合用户的当前提问、RAG 语义/关键词相关度，以及用户的个人偏好信号（浏览记录 `MLproduct_view`、收藏 `MLbuying`、历史购买 `MLorder` 与猫咪档案 `MLcat`），从在售有库存商品中筛选匹配度最高的 **3 款商品**，并在回复气泡下方渲染可交互商品卡片（支持点击查看详情或直接加入购物车）。
- **多商品横向对比与三件套套餐搭配**：支持对同品类多款猫粮/商品进行四维横向测评对比，或跨「猫咪主粮 + 猫咪零食 + 猫咪日用」三大类一键定制一站式三件套组合。
- **科学喂养与每日食量测算**：基于用户猫咪档案（体重 `kg`、年龄、生理状态：减重/室内/绝育/成猫/妊娠/哺乳/幼猫）按 RER/DER 能量公式测算每日建议干粮克数区间、分餐次数与饮水量，提供七日换粮法及软便/吐毛球/泪痕护理方案。
- **购物车结构诊断与凑单建议**：实时分析买家当前购物车已选商品的品类结构与总金额，针对缺失品类推荐 3 款互补凑单好物。
- **订单查询与对话式直接操作**：支持在对话中查询用户订单列表及实时状态、协助取消未发货订单（状态 `1~3`，自动恢复库存）或确认收货（状态 `4 -> 5`），以及解答 24 小时发货与 7 天无忧退换货售后问题。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DIFY_API_KEY` | 空 | Dify 应用 API Key（`app-...`）；留空或不可达时自动降级使用 Qwen / 本地智能规则回复 |
| `DIFY_BASE_URL` | `https://api.dify.ai/v1` | Dify Service API 地址（支持 Dify Cloud 或私有化部署地址） |
| `DIFY_TOOL_TOKEN` | 空 | 可选；Dify 工作流调用后端工具接口 `/ai-cs/dify-tools/recommend`、`/ai-cs/dify-tools/orders`、`/ai-cs/dify-tools/feeding-plan`、`/ai-cs/dify-tools/compare`、`/ai-cs/dify-tools/cart-diagnosis` 时的校验令牌 |


