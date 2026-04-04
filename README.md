# GGuide Backend

GGuide 是一个游戏推荐与浏览项目。当前仓库是后端服务，负责用户认证、游戏数据读取、收藏、最近浏览记录，以及给前端提供多语言游戏数据接口。

## 当前状态

- 后端技术栈：Spring Boot 4、Spring Security、Spring Data MongoDB
- 数据库：MongoDB Atlas
- 认证方式：JWT
- 默认后端地址：`http://localhost:8080`
- 对应前端项目：`D:\code\projects\gguide-frontend`

## 功能概览

- 用户注册 / 登录
- 游戏列表、游戏详情、分类筛选
- 收藏列表、收藏状态、添加收藏、取消收藏
- 最近浏览记录
- 游戏标题、简介、分类、地区的中英文展示
- 使用 Python 脚本从 Steam 拉取并整理游戏数据

## 目录说明

```text
src/main/java/person/hardy/gguide
├─ controller   接口入口
├─ service      业务逻辑
├─ repository   MongoDB 数据访问
├─ model        DTO / Entity / VO
├─ config       安全与 CORS 配置
└─ common       JWT、语言处理、通用工具

src/main/resources
├─ application.yml
├─ .env.example
└─ .env

scripts
├─ seed_steam_games.py
├─ migrate_game_i18n.py
└─ migrate_game_categories.py
```

## 运行环境

- JDK 21
- Maven 3.9+
- MongoDB Atlas 可访问
- Python 3.10+，仅在执行数据脚本时需要

`pom.xml` 当前目标版本是 Java 21，因此建议 IDE、终端里的 `JAVA_HOME`、Maven 使用同一套 JDK 21，避免出现“IDE 能跑、命令行不一致”的情况。

## 环境变量

后端环境变量文件放在 [src/main/resources/.env](D:/code/projects/gguide-backend/src/main/resources/.env)，示例文件在 [src/main/resources/.env.example](D:/code/projects/gguide-backend/src/main/resources/.env.example)。

示例内容：

```env
MONGO_DATABASE=
MONGO_USERNAME=
MONGO_PASSWORD=
MONGO_CLUSTER=
```

Mongo 连接串由 [application.yml](D:/code/projects/gguide-backend/src/main/resources/application.yml) 读取：

```yaml
spring:
  mongodb:
    uri: mongodb+srv://${env.MONGO_USERNAME}:${env.MONGO_PASSWORD}@${env.MONGO_CLUSTER}/${env.MONGO_DATABASE}?retryWrites=true&w=majority
```

## 启动后端

在当前目录执行：

```powershell
mvn spring-boot:run
```

启动后默认访问：

- 健康验证：`http://localhost:8080/api/games`
- 默认端口：`8080`

如果提示 `Port 8080 was already in use`，说明本机已经有一个后端实例在运行，先关闭旧进程，或者改端口再启动。

## 启动前端

前端项目在同级目录 `D:\code\projects\gguide-frontend`。

```powershell
cd D:\code\projects\gguide-frontend
npm install
npm run dev
```

前端当前在 [src/services/api.ts](D:/code/projects/gguide-frontend/src/services/api.ts) 中默认请求：

```ts
baseURL: 'http://localhost:8080'
```

所以联调时需要保证后端运行在本机 `8080` 端口。

## 数据脚本

### 1. 初始化 Steam 游戏数据

脚本：[scripts/seed_steam_games.py](D:/code/projects/gguide-backend/scripts/seed_steam_games.py)

作用：

- 从 Steam 获取指定游戏详情
- 写入 `games` 集合
- 自动补充中英文标题、简介、分类、评分、地区、封面、预告片、下载链接

运行前安装依赖：

```powershell
pip install pymongo
```

执行：

```powershell
cd D:\code\projects\gguide-backend
python scripts/seed_steam_games.py
```

### 2. 迁移多语言字段

脚本：[scripts/migrate_game_i18n.py](D:/code/projects/gguide-backend/scripts/migrate_game_i18n.py)

作用：

- 将旧字段 `title`、`description` 迁移到 `titleI18n`、`descriptionI18n`
- 同步修正分类和地区字段

### 3. 归一化分类字段

脚本：[scripts/migrate_game_categories.py](D:/code/projects/gguide-backend/scripts/migrate_game_categories.py)

作用：

- 将旧字段 `category` 迁移到 `categories`
- 对分类编码做统一清洗

## 主要接口

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`

### 游戏

- `GET /api/games`
- `GET /api/games/{id}`
- `GET /api/games/category/{category}`
- `POST /api/games`

### 收藏

- `GET /api/favorites`
- `GET /api/favorites/{gameId}/status`
- `POST /api/favorites/{gameId}`
- `DELETE /api/favorites/{gameId}`

### 最近浏览

- `GET /api/recently-viewed`
- `POST /api/recently-viewed/{gameId}`

## 鉴权说明

- 登录成功后，后端返回 JWT token
- 前端会把 token 放到 `Authorization: Bearer <token>` 请求头里
- `/api/auth/**` 和 `GET /api/games/**` 当前允许匿名访问
- 收藏和最近浏览接口需要登录

## 多语言说明

游戏相关接口支持 `lang` 参数，也会参考 `Accept-Language` 请求头。

示例：

```http
GET /api/games?lang=zh-CN
GET /api/games?lang=en-US
```

## 返回格式

接口统一使用 `ResultVO<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 当前已知事项

- JWT 密钥仍然是代码内硬编码，后续建议改成环境变量配置
- 前端 API 地址目前写死为 `http://localhost:8080`
- 后端测试目前以基础上下文测试为主，接口级和业务级测试还可以继续补强
