# 共享充电桩车位预约管理系统

## 项目简介
这是一个基于 **Spring Boot 3** 和 **Vue 3** 开发的智能共享充电桩车位预约管理系统。系统不仅支持用户通过 Web 端浏览充电站、预约车位、查看实时充电进度、支付订单，还深度集成了 **大语言模型 (LLM)**，提供了具备实时感知能力的 AI 助手，极大提升了用户体验。

## 🚀 核心特性与技术亮点

### 1. 🤖 AI Agent 智能助手 ( Function Calling )
* **需求理解与工具调用**：集成了兼容 OpenAI 规范的 API (SiliconFlow)，开发了基于 Spring 原生 RestClient 的 Agent 系统。AI 助手能够理解用户自然语言，自主决定调用后台工具 (如搜索充电站、查询空闲桩数等)。
* **地理位置感知 (LBS)**：前端自动获取用户 GPS 坐标并传递至后端，Agent 工具箱利用 **Haversine 公式** 实时计算附近充电站距离，并将结果按远近自动排序后喂给大模型，实现精确的“附近找桩”推荐。

### 2. 📚 RAG 知识库问答
* **私有知识增强**：内置了基于内存的高效 RAG (检索增强生成) 引擎。对充电指南、计费规则等文档进行文本分块 (Chunking) 与向量化 (Embedding)，利用余弦相似度算法检索最相关片段作为 AI 回答的上下文，解决了大模型针对特定业务的幻觉问题。

### 3. 🚦 动态车位与时间轴仿真
* **全动态资源状态计算**：车位状态完全摒弃传统的静态数据读取，改为基于订单时间轴实时计算。只有当物理硬件正常通信时，系统才会查询未来2小时内的活跃预约订单，精确动态推算“空闲”、“预约中”、“使用中”、“故障”四大状态。
* **物理防超卖控制**：底层依然使用 JPA 的 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 行级悲观锁，从根源上杜绝高并发下的资源抢占与重入问题。

### 4. ⚡ 物联网级实时状态推送
* **WebSocket 全双工通信**：利用 Spring Boot WebSocket 和 STOMP 协议，建立客户端与服务端的长连接通道。
* **内存数据仿真推流**：后台通过 `@Scheduled` 定时任务，实时计算并在 `ConcurrentHashMap` 中持有各充电桩的电压、电流、剩余电量 (SOC) 仿真进度，并在纳秒级别定点推送至各个正在充电的用户终端，让前端实现车辆充电的实时仪表盘效果。

### 5. 💰 金额高精度财务计算
* **全面使用 `BigDecimal`**：针对所有的基础价格、分时段计费规则（早晚高峰 1.5 倍，谷电 0.5 倍）、总价汇算等，彻底杜绝 `Double` 浮点精度雪崩，保证订单结算系统的绝对财务安全。

---

## 🛠 技术栈

### 后端架构
* **核心框架**: Spring Boot 3.2
* **编程语言**: Java 17
* **持久化**: Spring Data JPA / Hibernate
* **数据库**: H2 (内存数据库，快开模式) / MySQL (生产模式)
* **大模型交互**: FastJSON2, JDK 11+ HttpClient
* **安全鉴权**: Spring Security, JWT (无状态)
* **实时通信**: WebSocket + STOMP

### 前端架构
* **核心框架**: Vue 3 (Composition API)
* **构建与打包**: Vite
* **状态管理**: Pinia
* **路由控制**: Vue Router
* **网络请求**: Axios
* **地图组件**: 获取 HTML5 Geolocation API

---

## 🚦 快速运行指南

### 1. 配置 AI 密钥 (必须)
打开 `charging-station-system/src/main/resources/application.yml`，修改 `ai.siliconflow.api-key` 为你自己的 Key。

### 2. 启动后端 (Spring Boot)
```bash
# 确保已安装 JDK 17+ 和 Maven
cd charging-station-system
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
*本项目作为一个前后端分离 + AI 原生集成的全栈演示系统，持续迭代中。*
