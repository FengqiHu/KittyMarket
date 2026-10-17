This is Kitty online shopping system.
Welcome to use!
If you encountered any bugs, please contact us immediately, thanks!

## 本地运行（Vue + Spring Boot + MySQL + Redis）

使用 `maoliang-qianduan` 和 `maoliang-houduan`，其余目录是旧版 Servlet 项目。
后端需要 Java 17、Maven 和运行中的 MySQL；前端需要 Node.js 和 npm。Redis 用于热门商品缓存，未启动时仍可正常浏览和下单。

访问地址：前端 http://127.0.0.1:3000/，后端 http://127.0.0.1:8080/。
前端通过 `/api` 代理访问后端。

### 数据库

数据库名为 `maoliang`，字符集为 `utf8mb4`。在项目根目录执行：

```sh
mysql -h 127.0.0.1 -u root -p < database/create-database.sql
mysql -h 127.0.0.1 -u root -p maoliang < maoliang-houduan/src/main/resources/schema.sql
```

建表后，使用同一个脚本导入完整演示数据：

```sh
mysql -h 127.0.0.1 -u root -p maoliang < database/demo-data.sql
```

`demo-data.sql` 包含买卖家账号、全部 26 件商品（主粮 10 件、零食 8 件、日用 8 件）、商品历史记录、演示订单及猫咪档案。已有数据库也执行这一份脚本：按账号、卖家及商品名补齐缺失数据，保留已有账号密码、商品价格、库存和订单，并更新演示商品及历史记录的图片路径。脚本可重复执行，不依赖固定的用户或商品 ID。商品归属演示卖家 `123`。演示描述及适用范围仅用于展示。

26 件演示商品各有一张通过内置 ImageGen 生成的独立商品图，保存在 `maoliang-qianduan/public/demo/products/`，使用 WebP 格式，浏览时无需外网。商品、文件与完整生成提示词的对应关系见 `docs/product-images.json`。导入后按下文清除 Redis 热门缓存，即可立即显示更新。

后端启动时也会自动创建缺失的业务表，保留已有表和数据。
旧版 `Setup/Setup.java` 包含删表操作，不用于当前 MySQL 初始化。

| 演示角色 | 用户名 | 密码 |
| --- | --- | --- |
| 买家 | 111 | 111 |
| 卖家 | 123 | 123 |

连接凭据保存在 `maoliang-houduan/application-local.properties`（已加入 Git 忽略）：

```properties
spring.datasource.username=root
spring.datasource.password=你的本机MySQL密码
```

也可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` 配置连接；使用环境变量时应移除本地文件中的对应凭据覆盖。

### Redis 与热门商品

在项目根目录启动 Redis（本机已有 Redis 时可跳过）：

```sh
docker compose up -d redis
```

Redis 仅绑定本机地址，内存上限 128MB，使用 LRU 淘汰缓存，不持久化业务数据。

商城首页的“热门商品”调用 `GET /good/hot-goods`，返回最多 8 件在售且有库存的商品。按未取消订单的商品数量、收藏人数、商品 ID 依次降序排列；暂无销量或收藏时展示最新商品。热门列表及商品信息以 JSON 缓存在 Redis，MySQL 负责真实价格和库存校验。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | 空 | Redis ACL 用户及密码 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `HOT_GOODS_CACHE_ENABLED` | `true` | 设为 `false` 时直接查 MySQL |
| `HOT_GOODS_CACHE_TTL` | `5m` | 缓存有效期，至少 `1ms` |

新增、删除、改价、收藏、下单及取消订单都会清除热门缓存；数据库事务中的操作在提交成功后清除，回滚时保留缓存。Redis 使用版本号和 Lua 脚本，防止并发查询在清除后回填旧数据。Redis 连接和命令超时均为 300ms，失败后热门查询在 10 秒内直接回源 MySQL；缓存故障不会导致下单失败。Redis 故障期间若清除失败，旧展示数据最多保留到原有 TTL 到期，下单仍以 MySQL 为准。

手动修改数据库或导入演示数据后，可等待 TTL 到期，或同时清除本项目热门缓存及版本标记（有认证或非默认端口时补充相应连接参数）：

```sh
redis-cli DEL 'kittymarket:{hot-goods}:v1' 'kittymarket:{hot-goods}:v1:generation'
```

后端测试（需要本机 MySQL；第二条同时需要 Redis，会创建并清理测试数据）：

```sh
cd maoliang-houduan
mvn test
KITTY_REDIS_INTEGRATION=true mvn test
```

### Qwen 与 RAG 检索

后端使用 LangChain4j 将在售商品记录构造成检索片段，以 Qwen 向量模型进行语义检索，再把命中的商品事实交给 Qwen 生成简短说明。`GET /good/rag-search?query=...&kind=...` 用于买家商品检索；`GET /cat/recommend/{catId}` 根据当前登录用户指定的猫咪档案推荐主粮。推荐会先校验品种、年龄、体重、库存和热量，商品 ID 与建议喂食量始终由数据库及现有公式确定。

启动后端前设置以下环境变量。密钥应只配置在后端环境，不能写入前端代码或提交到 Git：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 空 | 阿里云百炼 API Key；留空时使用本地检索与规则推荐 |
| `DASHSCOPE_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 与 API Key 所在地域一致的 OpenAI 兼容接口地址；工作空间域名可在百炼控制台获取 |
| `QWEN_CHAT_MODEL` | `qwen-plus` | 生成检索说明的模型 |
| `QWEN_EMBEDDING_MODEL` | `text-embedding-v4` | 商品与查询的向量模型 |

商品向量索引保存在后端内存中，首次检索时建立，商品内容变化时重建。Qwen 调用不可用时会退回本地关键词检索或规则推荐，仍不会返回下架、无库存或不适用的推荐商品。配置密钥后，商品文案以及所选猫咪的档案信息会发送至所配置的百炼接口。

参考：[LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag/)、[阿里云百炼向量模型](https://help.aliyun.com/en/model-studio/embedding)。

### 启动

在一个终端启动后端（从后端目录运行，以加载本地凭据）：

```sh
cd maoliang-houduan
mvn clean package
java -jar target/maoliang-0.0.1-SNAPSHOT.war --server.address=127.0.0.1
```

已打包后重启只需执行 `java -jar` 命令。

在另一个终端启动前端：

```sh
cd maoliang-qianduan
npm ci
npm run serve -- --host 127.0.0.1
```

在对应终端按 `Ctrl+C` 停止服务。

### 界面与验证

前端使用适配 Vue 3 的 Element Plus，统一了登录注册、买家商城、购物车、收藏、猫咪档案和卖家后台的布局、表格、表单及操作反馈。商城使用顶部导航和商品分类，商家后台使用侧栏和紧凑表格；支持窄屏菜单，页面和组件按需加载。

```sh
cd maoliang-qianduan
npm run lint -- --no-fix
npm run build
```

结算已接入 `/order/createorder-control`：校验登录和收货信息，在同一数据库事务中创建订单并扣减库存，成功后进入“我的订单”。从购物车结算会扣减购物车数量并保留收藏；发货前取消订单会恢复库存。后端启动时会自动为旧订单表补充收货人字段，保留原有数据。

猫咪档案支持新增、编辑和删除；商品检索与指定猫咪推荐支持 Qwen RAG，并可在未配置密钥时使用本地检索。
