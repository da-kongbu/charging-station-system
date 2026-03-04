# 共享充电桩车位预约管理系统

## 项目简介
这是一个基于 **Spring Boot 3** 和 **Vue 3** 开发的共享充电桩车位预约管理系统。系统支持用户通过 Web 端浏览充电站、预约车位、查看实时充电进度、支付订单，同时提供管理员后台进行站点和用户管理。

## 技术栈
### 后端
* **框架**: Spring Boot 3.2
* **语言**: Java 17
* **持久层**: Spring Data JPA / Hibernate
* **数据库**: H2 (内存数据库，默认开发使用) / MySQL (可通过配置文件切换)
* **连接池**: HikariCP (Spring Boot 默认)
* **安全与认证**: Spring Security, JWT (JSON Web Tokens)
* **实时通信**: WebSocket + STOMP 协议

### 前端
* **框架**: Vue 3 (Composition API)
* **构建工具**: Vite
* **状态管理**: Pinia
* **路由**: Vue Router
* **网络请求**: Axios

## 核心业务与技术实现

### 1. 资源状态流转与防超卖控制
* **基于数据库行级排他锁 (`FOR UPDATE`)**：在用户发起插枪充电或订单结算等涉及到关键资源状态变更的操作时，使用 JPA 底层的 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 实现悲观锁，防止高并发场景下的数据脏读和资源重入。
* **业务逻辑层时间校验**：在车位预约创建阶段，通过严格比对预约起止时间是否有交集，从业务逻辑层面防止同一时段的资源重叠预约。

### 2. 物联网级实时状态推送仿真
* **WebSocket 全双工通信**：利用 Spring Boot WebSocket 和 STOMP 协议，建立客户端与服务端的长连接通道。
* **内存数据仿真推流**：在后台通过 `@Scheduled` 定时任务，实时计算并在内存中（`ConcurrentHashMap`）持有各充电桩的电压、电流、剩余电量 (SOC) 仿真进度，并在纳秒级别定点推送至各个正在充电的用户终端，让前端实现车辆充电大屏数据的实时跑马更新效果。

### 3. 金额高精度财务计算
* **全面使用 `BigDecimal`**：针对计费模块所有的价格计算、总价汇算等，彻底杜绝 `Double` 浮点精度雪崩，保证订单财务结算数据的绝对准确。

### 4. 前后端分离与无状态认证
* **JWT 鉴权体系**：后端配置 JWT 过滤器链，拦截无有效 Token 的请求，实现无状态跨域认证与角色权限分配。
* **统一 API 响应契约**：构建了标准化的 `ApiResponse` 模型类与全局异常 `@ExceptionHandler` 拦截，前台后台依靠状态码及自定义业务编码无缝对接解耦。

## 快速开始

### 1. 启动后端
```bash
# 确保已安装 JDK 17+
cd charging-station-system
mvn spring-boot:run
```
后端服务将在 `http://localhost:8080/api` 启动。

### 2. 启动前端
```bash
# 确保已安装 Node.js 18+
cd vue-frontend
npm install
npm run dev
```
前端页面默认将在 `http://localhost:5173` 启动。

### 3. 测试账号
* **管理员**: `admin` / `admin123`
* **普通用户**: `user` / `user123`
