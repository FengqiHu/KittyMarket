This is Kitty online shopping system.
Welcome to use!
If you encountered any bugs, please contact us immediately, thanks!

## 本地运行（Vue + Spring Boot + MySQL）

使用 `maoliang-qianduan` 和 `maoliang-houduan`，其余目录是旧版 Servlet 项目。
后端需要 Java 17、Maven 和运行中的 MySQL；前端需要 Node.js 和 npm。

访问地址：前端 http://127.0.0.1:3000/，后端 http://127.0.0.1:8080/。
前端通过 `/api` 代理访问后端。

### 数据库

数据库名为 `maoliang`，字符集为 `utf8mb4`。在项目根目录执行：

```sh
mysql -h 127.0.0.1 -u root -p < database/create-database.sql
mysql -h 127.0.0.1 -u root -p maoliang < maoliang-houduan/src/main/resources/schema.sql
```

仅在首次初始化的空数据库中导入演示数据：

```sh
mysql -h 127.0.0.1 -u root -p maoliang < database/demo-data.sql
```

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

原项目尚未实现新增猫咪档案接口，对应页面已明确显示状态。
