# 共享充电桩车位预约管理系统

## 项目简介

这是一个基于 **Spring Boot 3** 和 **Vue 3 + Vuetify 3** 开发的智能共享充电桩车位预约管理系统。系统不仅支持用户通过 Web 端浏览充电站、预约车位、查看实时充电进度、支付订单，还深度集成了 **大语言模型 (LLM) Agent**，提供了具备实时感知能力的 AI 智能助手，极大提升了用户体验。

## 核心特性与技术亮点

### 1. AI Agent 智能助手 ( Function Calling )

* **统一 Agent 架构**：基于兼容 OpenAI 规范的 API (SiliconFlow)，开发了 `AgentService` 统一智能体服务。AI 助手能够理解用户自然语言，通过 Function Calling 自主决策调用后台工具（搜索充电站、查询空闲桩数、查询用户预约、推荐预约方案等），并返回包含富卡片数据的结构化响应。
* **多轮对话上下文**：前端携带对话历史，Agent 拥有完整的上下文记忆，支持连续追问、预约确认等复杂多轮交互场景。
* **富卡片渲染**：Agent 返回结构化卡片数据（充电站卡片、空闲桩位卡片、预约详情卡片、推荐预约卡片），前端根据类型渲染为 Material Design 风格的交互组件。
* **地理位置感知 (LBS)**：前端自动获取用户 GPS 坐标并传递至后端，`StationDiscoveryService` 利用 Haversine 公式实时计算附近充电站距离，按远近排序后提供给大模型，实现精确的"附近找桩"推荐。

### 2. RAG 知识库问答

* **私有知识增强**：内置基于内存的高效 RAG (检索增强生成) 引擎。对充电指南、计费规则等文档进行文本分块 (Chunking) 与向量化 (Embedding)，利用余弦相似度算法检索最相关片段作为 AI 回答的上下文，解决大模型针对特定业务的幻觉问题。

### 3. Vuetify 3 Material Design 前端

* **组件化 UI 框架**：前端全面采用 Vuetify 3 组件库，实现 Material Design 风格的用户界面，包含统一的配色主题（绿色主色调）、响应式布局、丰富的交互反馈。
* **智能搜索状态管理**：通过 Pinia `smartSearch` Store 管理 AI 推荐结果，使推荐数据跟随"任务"而非"页面"存在，跨页面保持搜索状态。
* **用户端 / 管理端双入口**：用户端包含首页充电站浏览、站点详情、预约管理、订单管理、AI 助手聊天等页面；管理端包含仪表盘、站点管理、用户管理、订单管理等后台功能。

### 4. 动态车位与时间轴仿真

* **全动态资源状态计算**：车位状态基于订单时间轴实时计算。系统查询未来 2 小时内的活跃预约订单，精确动态推算"空闲"、"预约中"、"使用中"、"故障"四大状态。
* **物理防超卖控制**：底层使用 JPA 的 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 行级悲观锁，从根源上杜绝高并发下的资源抢占与重入问题。

### 5. 物联网级实时状态推送

* **WebSocket 全双工通信**：利用 Spring Boot WebSocket 和 STOMP 协议，建立客户端与服务端的长连接通道。
* **内存数据仿真推流**：后台通过 `@Scheduled` 定时任务，实时计算各充电桩的电压、电流、剩余电量 (SOC) 仿真进度，在纳秒级别推送至各个正在充电的用户终端，实现车辆充电的实时仪表盘效果。

### 6. 金额高精度财务计算

* **全面使用 `BigDecimal`**：针对基础价格、分时段计费规则（早晚高峰 1.5 倍，谷电 0.5 倍）、总价汇算等，彻底杜绝浮点精度问题，保证订单结算系统的财务安全。

---

## 技术栈

### 后端架构

| 类别 | 技术 |
| --- | --- |
| 核心框架 | Spring Boot 3.2 |
| 编程语言 | Java 17 |
| 持久化 | Spring Data JPA / Hibernate |
| 数据库 | H2 (内存数据库，开发模式) / MySQL (生产模式) |
| 大模型集成 | SiliconFlow API (DeepSeek-V3)，FastJSON2，JDK HttpClient |
| 向量检索 | BAAI/bge-m3 Embedding (SiliconFlow) |
| 安全鉴权 | Spring Security, JWT (无状态) |
| 实时通信 | WebSocket + STOMP |
| API 文档 | SpringDoc OpenAPI (Swagger) |

### 前端架构

| 类别 | 技术 |
| --- | --- |
| 核心框架 | Vue 3 (Composition API) |
| UI 组件库 | Vuetify 3 (Material Design) |
| 图标 | Material Design Icons (@mdi/font) |
| 构建与打包 | Vite |
| 状态管理 | Pinia |
| 路由控制 | Vue Router |
| 网络请求 | Axios |
| 实时通信 | @stomp/stompjs + sockjs-client |
| 地理位置 | HTML5 Geolocation API |

---

## 项目结构

```
charging-station-system/
├── src/main/java/com/charging/
│   ├── config/            # 安全、数据初始化等配置
│   ├── controller/        # REST 控制器（用户端 + 管理端 + AI Agent）
│   ├── dto/               # 数据传输对象
│   │   └── agent/         # Agent 专用 DTO（请求、响应、富卡片数据）
│   ├── entity/            # JPA 实体
│   ├── exception/         # 全局异常处理
│   ├── repository/        # Spring Data JPA 仓库
│   ├── security/          # JWT 认证与授权
│   └── service/           # 业务逻辑层
│       ├── AgentService.java           # AI Agent 核心（Function Calling）
│       ├── RagService.java             # RAG 知识库检索
│       └── StationDiscoveryService.java # 充电站发现与推荐
├── src/main/resources/
│   ├── application.yml    # 主配置
│   └── knowledge/         # RAG 知识库文档
└── vue-frontend/
    ├── src/
    │   ├── components/    # 公共组件（AiChat, ChargingMonitor, Header 等）
    │   ├── views/
    │   │   ├── user/      # 用户端页面（Home, Login, Register, Orders, Reservations 等）
    │   │   └── admin/     # 管理端页面（Dashboard, Stations, Users, Orders）
    │   ├── stores/        # Pinia 状态管理（auth, smartSearch）
    │   ├── composables/   # 组合式函数（useLocation）
    │   ├── plugins/       # Vuetify 配置
    │   └── api/           # Axios 请求封装
    └── package.json
```

---

## 快速运行指南

### 1. 配置 AI 密钥 (必须)

打开 `src/main/resources/application.yml`，修改 `ai.siliconflow.api-key` 为你自己的 Key，或通过环境变量 `AI_SILICONFLOW_API_KEY` 设置。

### 2. 启动后端 (Spring Boot)

```bash
# 确保已安装 JDK 17+ 和 Maven
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080/api` 启动。

### 3. 启动前端 (Vue 3)

```bash
# 确保已安装 Node.js 18+
cd vue-frontend
npm install
npm run dev
```

前端页面默认将在 `http://localhost:5173` 启动。

### 4. 测试账号体验

* **管理员**: `admin` / `admin123`
* **普通用户**: `user` / `user123`

---

## API 文档

启动后端后，访问 Swagger UI 查看完整的 API 文档：

```
http://localhost:8080/swagger-ui.html
```

---

*本项目作为前后端分离 + AI 原生集成的全栈演示系统，持续迭代中。*
