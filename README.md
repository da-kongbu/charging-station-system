# 共享充电桩车位预约管理系统

## 项目简介
这是一个基于 **Spring Boot 3** 和 **Vue 3** 开发的共享充电桩车位预约管理系统。系统支持用户通过 Web 端浏览充电站、预约车位、支付订单，同时提供管理员后台进行站点和用户管理。

## 技术栈
- **后端**：Spring Boot 3.2, Spring Security, JPA, MySQL/H2
- **前端**：Vue 3, Vite, Pinia, Vue Router, Axios
- **数据库**：H2 (默认开发环境) / MySQL (生产环境)

## 快速开始

### 1. 启动后端
```bash
# 确保已安装 JDK 17+
cd .
mvn spring-boot:run
```
后端服务将在 `http://localhost:8080` 启动。
- API 文档: `http://localhost:8080/swagger-ui.html`
- H2 控制台: `http://localhost:8080/h2-console`

### 2. 启动前端
```bash
# 确保已安装 Node.js 18+
cd vue-frontend
npm install
npm run dev
```
前端页面将在 `http://localhost:5173` 启动。

### 3. 测试账号
- **管理员**: `admin` / `admin123`
- **普通用户**: `user` / `user123`

## 如何上传到 GitHub

1. 在 GitHub 上创建一个新的仓库（Empty Repository）。
2. 在本项目根目录下运行以下命令（替换 `YOUR_GITHUB_REPO_URL` 为你的仓库地址）：

```bash
# 关联远程仓库
git remote add origin YOUR_GITHUB_REPO_URL

# 推送代码到 main 分支
git branch -M main
git push -u origin main
```
