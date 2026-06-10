# 闻道管理系统 -- Docker 部署指南（公网 IP 版）

> 适用于阿里云 CentOS 服务器，通过公网 IP 直接访问。适合短期开发/演示场景，无需域名和 SSL。

---

## 1. 概述

### 1.1 项目架构

```
                         Internet
                            |
                            v
                   +------------------+
                   |  Nginx :80       |
                   |  (前端静态文件    |
                   |   + 反向代理)    |
                   +--------+---------+
                            |
                            v
                   +------------------+       +------------------+       +------------------+
                   |  Spring Boot     | ----> |  MySQL 8.0       |       |  Redis 7         |
                   |  :8080 (后端API)  |       |  :3306           |       |  :6379           |
                   +------------------+       +------------------+       +------------------+
                            |
                            v
                   +------------------+
                   |  File System     |
                   |  上传文件 / 日志   |
                   +------------------+
```

### 1.2 服务拓扑说明

| 服务 | 容器名 | 内部端口 | 暴露端口 | 用途 |
|------|--------|----------|----------|------|
| Nginx (前端) | `wendao-frontend` | 80 | 3000 | 静态文件服务 + API 反向代理 + WebSocket 代理 |
| Spring Boot (后端) | `wendao-backend` | 8080 | - | REST API + WebSocket |
| MySQL 8.0 | `wendao-mysql` | 3306 | - | 业务数据库 |
| Redis 7 | `wendao-redis` | 6379 | - | 缓存 + JWT Token 存储 |

所有服务通过内部桥接网络 `wendao-net` 通信。Nginx 将 `/prod-api/` 反向代理到 `http://backend:8080/`，浏览器只与 Nginx 的 3000 端口通信。

### 1.3 适用场景

- **操作系统：** CentOS 7/8/9、Rocky Linux、AlmaLinux
- **云平台：** 阿里云 ECS（推荐 2 核 4G 及以上）
- **访问方式：** 浏览器直接访问 `http://<服务器公网IP>:3000`
- **定位：** 短期演示、开发测试、内网部署
- **安全前提：** 仅开放 3000 端口对外，其他端口通过安全组限制

---

## 2. 文件结构

所有 Docker 相关文件位于项目根目录下：

```
WenDao/
├── Dockerfile.backend                  # 后端多阶段构建（Maven + JDK21）
├── Dockerfile.frontend                 # 前端多阶段构建（Node + Nginx）
├── docker-compose.yml                  # 4 服务编排
├── .env.docker.example                 # 环境变量模板（提交到 Git）
├── .env.docker                         # 实际环境变量（不提交，含密钥）
├── .dockerignore                       # 构建上下文排除规则
├── deploy.sh                           # 一键部署脚本
├── update-backend.sh                   # 单独更新后端
├── update-frontend.sh                  # 单独更新前端
├── docker/
│   ├── nginx/
│   │   ├── nginx.conf                  # Nginx 主配置
│   │   └── default.conf.template       # HTTP 站点配置模板
│   ├── backend/
│   │   └── logback-docker.xml          # Docker 日志配置
│   ├── redis/
│   │   └── redis.conf                  # Redis 持久化配置
│   └── mysql/
│       └── init/
│           ├── 01-init.sql             # 字符集设置
│           └── 02-wendao.sql           # 业务表结构+数据（由 deploy.sh 复制）
```

---

## 3. 前置准备

### 3.1 阿里云 ECS 服务器要求

| 项目 | 最低配置 | 推荐配置 |
|------|----------|----------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 系统盘 | 40 GB | 80 GB |
| 操作系统 | CentOS 7.9+ / Rocky Linux 8+ | Rocky Linux 9 |
| 带宽 | 1 Mbps | 3 Mbps+ |

### 3.2 安全组规则

| 端口 | 方向 | 协议 | 来源 | 用途 |
|------|------|------|------|------|
| 22 | 入方向 | TCP | 你的 IP | SSH 远程管理 |
| 3000 | 入方向 | TCP | 0.0.0.0/0 | HTTP 访问 |

> 其他端口（3306、6379、8080）**不要**在安全组中开放——它们只在 Docker 内部网络通信，暴露到公网有安全风险。

### 3.3 Docker 和 Docker Compose 安装

```bash
# 1. 卸载旧版本（如有）
sudo yum remove -y docker docker-client docker-client-latest \
    docker-common docker-latest docker-latest-logrotate \
    docker-logrotate docker-engine

# 2. 安装 Docker
curl -fsSL https://get.docker.com | bash

# 3. 启动并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker

# 4. 安装 Docker Compose 插件
sudo yum install -y docker-compose-plugin

# 5. 验证安装
docker --version
docker compose version

# 6. 将当前用户加入 docker 组（避免每次 sudo）
sudo usermod -aG docker $USER
# 退出重新登录后生效
```

---

## 4. 快速开始

以下 4 步即可完成部署：

```bash
# ==== 步骤 1：克隆项目 ====
git clone <your-repo-url> /opt/wendao
cd /opt/wendao

# ==== 步骤 2：配置环境变量 ====
cp .env.docker.example .env.docker
vim .env.docker
# 必须修改：
#   DB_PASSWORD=your-strong-password    ← 数据库密码
#   TOKEN_SECRET=<64位随机字符串>        ← JWT 密钥
#   NEWS_AI_API_KEY=sk-xxxxx            ← DeepSeek API Key（如需 AI 功能）

# ==== 步骤 3：一键部署 ====
chmod +x deploy.sh update-backend.sh update-frontend.sh
./deploy.sh

# ==== 步骤 4：访问系统 ====
# 浏览器打开 http://<服务器公网IP>:3000
# 默认账号：admin / admin123
```

---

## 5. 详细配置说明

### 5.1 环境变量说明（.env.docker）

#### 数据库配置

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `DB_USERNAME` | `root` | 否 | MySQL 用户名 |
| `DB_PASSWORD` | `root` | **是** | MySQL root 密码，务必修改 |

#### Redis 配置

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `REDIS_PASSWORD` | 空 | 否 | Redis 密码，默认无密码 |

#### 后端安全密钥

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `WENDAO_AES_KEY` | `WenDao2026!AesKey#Secret@9876` | **是** | AES 加密密钥（恰好 32 字符），用于加密 API Key 等敏感字段 |
| `TOKEN_SECRET` | `change-me-to-...` | **是** | JWT 签名密钥（建议 64 位随机字符串），所有用户登录依赖此密钥 |
| `TOKEN_EXPIRE_TIME` | `30` | 否 | JWT Token 过期时间（分钟） |

#### AI 功能配置

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `NEWS_AI_API_KEY` | 空 | 否 | DeepSeek API Key，获取地址：https://platform.deepseek.com |

#### 生产安全配置

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `SWAGGER_ENABLED` | `false` | 否 | 是否公开 Swagger 文档，生产环境应关闭 |
| `DRUID_STAT_ENABLED` | `false` | 否 | 是否公开 Druid 控制台，生产环境应关闭 |
| `LOG_LEVEL_COM_WENDAO` | `info` | 否 | 应用日志级别，`debug` 会输出更多信息 |

#### 文件路径

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `WENDAO_PROFILE` | `/home/wendao/uploadPath` | 否 | 文件上传路径（容器内） |
| `LOG_PATH` | `/home/wendao/logs` | 否 | 日志路径（容器内） |

#### 镜像版本

| 变量名 | 默认值 | 必填 | 说明 |
|--------|--------|------|------|
| `BACKEND_VERSION` | `latest` | 否 | 后端镜像 Tag |
| `FRONTEND_VERSION` | `latest` | 否 | 前端镜像 Tag |

#### 生成随机密钥

```bash
# 生成 64 位随机 TOKEN_SECRET
openssl rand -base64 48

# 生成 32 字符 AES_KEY
openssl rand -base64 24 | cut -c1-32
```

### 5.2 配置外部化原理（SPRING_APPLICATION_JSON）

后端配置写在 `application-druid.yml` 等文件中，但硬编码值（数据库地址 `localhost`、密码等）不适用于 Docker 环境。

**解决方案：** `docker-compose.yml` 通过 `SPRING_APPLICATION_JSON` 环境变量覆盖所有硬编码配置。

```yaml
environment:
  SPRING_APPLICATION_JSON: >-
    {
      "spring.datasource.druid.master.url": "jdbc:mysql://mysql:3306/wendao?...",
      "spring.datasource.druid.master.username": "${DB_USERNAME:-root}",
      "spring.datasource.druid.master.password": "${DB_PASSWORD:-root}",
      "spring.data.redis.host": "redis",
      ...
    }
```

**关键点：**
- **服务名即主机名：** Docker 内部网络中，`mysql` 和 `redis` 就是服务名
- **优先级最高：** `SPRING_APPLICATION_JSON` 高于 YAML 文件中的配置
- **无需修改源码：** 所有硬编码值被环境变量覆盖

### 5.3 Nginx 配置说明

```nginx
server {
    listen 80;
    server_name _;    # 匹配所有请求（IP 或域名均可）

    # SPA 路由：所有路径返回 index.html（Vue Router history 模式）
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # 大屏页面
    location /screen {
        alias /usr/share/nginx/html;
        try_files $uri $uri/ /screen.html;
    }

    # API 反向代理：/prod-api/xxx → http://backend:8080/xxx
    location /prod-api/ {
        proxy_pass http://backend:8080/;
        proxy_read_timeout 120s;    # SSE 流式响应需要长超时
        proxy_buffering off;        # 关闭缓冲，支持 SSE
    }

    # WebSocket 代理
    location /ws {
        proxy_pass http://backend:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400s;  # 24 小时长连接
    }
}
```

`proxy_buffering off` 是关键——AI 解读使用 SSE 流式返回，关闭缓冲后用户才能看到逐字输出效果。

---

## 6. 部署步骤（deploy.sh 详解）

### 步骤 0：检查前置条件

```bash
docker --version
docker compose version
```

### 步骤 1：配置 .env.docker

```bash
cp .env.docker.example .env.docker
vim .env.docker
# 修改 DB_PASSWORD、TOKEN_SECRET、NEWS_AI_API_KEY
```

### 步骤 2：创建目录并复制 SQL

```bash
mkdir -p docker/nginx/ssl docker/nginx/certbot docker/mysql/init
cp console/sql/wendao.sql docker/mysql/init/02-wendao.sql
```

MySQL 容器首次启动时自动执行 `/docker-entrypoint-initdb.d/` 下的 SQL 文件。如果数据卷已有数据，不会重复执行。

### 步骤 3：构建并启动

```bash
docker compose build --no-cache backend frontend
docker compose up -d
```

**首次构建时间：**
- Maven 依赖下载 + 编译：5-10 分钟
- 前端依赖下载 + 构建：3-5 分钟

### 步骤 4：验证服务状态

```bash
docker compose ps
# 预期：4 个服务全部 "Up" 且 "(healthy)"
```

### 步骤 5：获取服务器公网 IP 并访问

```bash
# 查看公网 IP
curl -s ifconfig.me

# 浏览器打开 http://<公网IP>
# 账号：admin / admin123
```

---

## 7. 日常运维

### 7.1 服务启停

```bash
docker compose up -d                    # 启动所有服务
docker compose down                     # 停止（保留数据）
docker compose down -v                  # 停止并删除数据卷（危险！）
docker compose restart backend          # 快速重启后端
docker compose up -d --build backend    # 重新构建并启动后端
docker compose ps                       # 查看状态
docker stats                            # 查看资源占用
```

### 7.2 日志查看

```bash
docker compose logs -f                  # 实时查看所有日志
docker compose logs -f backend          # 只看后端
docker compose logs --tail=100 backend  # 最近 100 行

# 查看持久化的应用日志文件
ls /var/lib/docker/volumes/wendao-log-data/_data/
tail -f /var/lib/docker/volumes/wendao-log-data/_data/sys-error.log
```

### 7.3 单独更新后端

```bash
./update-backend.sh
# 流程：git pull → 重新构建 → 仅重启后端（不影响其他服务）
```

### 7.4 单独更新前端

```bash
./update-frontend.sh
# Nginx 重启毫秒级，几乎无感知
```

### 7.5 版本回滚

```bash
# 部署时打标签
docker tag wendao-backend:latest wendao-backend:v1.0.0

# 回滚：编辑 .env.docker，设置 BACKEND_VERSION=v1.0.0
docker compose up -d --no-deps backend
```

### 7.6 数据备份

```bash
# MySQL 备份
docker exec wendao-mysql mysqldump -uroot -p<密码> --single-transaction wendao \
    > wendao_backup_$(date +%Y%m%d_%H%M%S).sql

# 恢复
docker exec -i wendao-mysql mysql -uroot -p<密码> wendao < wendao_backup.sql

# 上传文件备份
tar -czf upload_backup_$(date +%Y%m%d).tar.gz \
    /var/lib/docker/volumes/wendao-upload-data/_data/
```

---

## 8. 常见问题（FAQ）

### 8.1 构建失败

**Maven 依赖下载慢或超时：** 根 pom.xml 已配置阿里云 Maven 镜像，通常无问题。如仍超时，可在服务器配置 Docker 镜像加速：

```bash
# /etc/docker/daemon.json
{
  "registry-mirrors": ["https://docker.1ms.run", "https://docker.xuanyuan.me"]
}
sudo systemctl restart docker
```

### 8.2 端口冲突

前端使用 3000 端口，一般不会冲突。如需修改，编辑 `docker-compose.yml` 中 `frontend.ports` 的 `"3000:80"` 改为 `"<宿主机端口>:80"`。

### 8.3 数据库连接失败

```bash
# 确认 MySQL 容器是否健康
docker compose ps mysql

# 确认数据库已初始化
docker exec wendao-mysql mysql -uroot -p<密码> -e "SHOW DATABASES;"

# 确认后端能连通 MySQL
docker exec wendao-backend sh -c "echo > /dev/tcp/mysql/3306 && echo ok"
```

### 8.4 前端 API 请求 404

```bash
# 检查 Nginx 配置
docker exec wendao-frontend cat /etc/nginx/conf.d/default.conf | grep prod-api

# 测试 Nginx → 后端连通性
docker exec wendao-frontend wget -qO- http://backend:8080/captchaImage
```

### 8.5 WebSocket 连接失败

大屏实时推送不工作时，检查 Nginx 配置中是否包含 WebSocket 升级头：

```bash
docker exec wendao-frontend cat /etc/nginx/conf.d/default.conf | grep -A5 "/ws"
# 必须包含 Upgrade 和 Connection 头
```

### 8.6 内存不足

```bash
# 查看内存使用
free -h
docker stats

# 降低 JVM 内存（编辑 .env.docker）
# JAVA_OPTS: "-Xms256m -Xmx512m -XX:MetaspaceSize=64m -XX:MaxMetaspaceSize=256m ..."

# 降低 Redis 内存（编辑 docker/redis/redis.conf）
# maxmemory 128mb

# 清理 Docker 占用
docker system prune -a
```

### 8.7 无法从公网访问

```bash
# 1. 确认服务正常运行
docker compose ps

# 2. 确认安全组已开放 3000 端口（阿里云控制台检查）

# 3. 确认服务器防火墙
firewall-cmd --list-ports
firewall-cmd --add-port=80/tcp --permanent
firewall-cmd --reload

# 4. 从服务器本地测试
curl http://localhost
```

---

## 9. 附录

### 9.1 健康检查说明

| 服务 | 检查方式 | 间隔 | 超时 | 重试 | 启动等待 |
|------|----------|------|------|------|----------|
| MySQL | `mysqladmin ping` | 10s | 5s | 10 | 60s |
| Redis | `redis-cli ping` | 10s | 5s | 5 | 10s |
| Backend | `curl /captchaImage` | 30s | 10s | 5 | 90s |
| Frontend | Nginx 进程 | Docker 自动管理 | - | - | - |

### 9.2 数据卷说明

| 卷名称 | 宿主机路径 | 容器内路径 | 内容 |
|--------|-----------|------------|------|
| `wendao-mysql-data` | `/var/lib/docker/volumes/wendao-mysql-data/_data/` | `/var/lib/mysql` | 数据库文件 |
| `wendao-redis-data` | `/var/lib/docker/volumes/wendao-redis-data/_data/` | `/data` | Redis 持久化 |
| `wendao-upload-data` | `/var/lib/docker/volumes/wendao-upload-data/_data/` | `/home/wendao/uploadPath` | 上传文件 |
| `wendao-log-data` | `/var/lib/docker/volumes/wendao-log-data/_data/` | `/home/wendao/logs` | 应用日志 |

### 9.3 安全建议

1. **安全组严格限制：** 仅开放 3000（HTTP）和 22（SSH，仅你的 IP）
2. **修改所有默认密钥：** `DB_PASSWORD`、`TOKEN_SECRET`、`WENDAO_AES_KEY`
3. **Swagger/Druid 默认关闭：** 生产环境 `SWAGGER_ENABLED=false`、`DRUID_STAT_ENABLED=false`
4. **定期更新镜像：** `docker compose build --no-cache --pull`
5. **定期备份数据库：** 参考 7.6 节

### 9.4 后续升级到域名+HTTPS

当你需要长期使用时，可以在此基础上添加域名和 HTTPS：

1. 在阿里云购买域名并备案
2. 添加 DNS A 记录指向服务器 IP
3. 修改 `.env.docker` 中 `DOMAIN` 为你的域名
4. 将 `docker-compose.yml` 中 Nginx 模板切换为 `default-ssl.conf.template`
5. 使用阿里云免费 SSL 证书或 Let's Encrypt 获取证书
6. 重启前端：`docker compose up -d --no-deps frontend`

### 9.5 Docker 常用命令速查

```bash
# 容器管理
docker compose up -d                    # 启动所有服务
docker compose down                     # 停止并删除容器
docker compose restart <service>        # 重启指定服务
docker compose ps                       # 查看状态

# 构建
docker compose build                    # 构建（使用缓存）
docker compose build --no-cache         # 完全重建

# 日志
docker compose logs -f                  # 实时日志
docker compose logs --tail=50 backend   # 最近 50 行

# 进入容器
docker exec -it wendao-backend sh       # 进入后端
docker exec -it wendao-mysql mysql -uroot -p  # MySQL 命令行

# 清理
docker system prune -a                  # 清理未使用资源
docker image ls                         # 查看镜像
```

---

> 文档版本：v1.1 | 最后更新：2026-06-10 | 公网 IP 版
