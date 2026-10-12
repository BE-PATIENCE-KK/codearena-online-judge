# CodeArena

CodeArena 是一个基于 Spring Boot 开发的轻量级在线判题平台学习项目，提供用户认证、题目浏览、代码提交、基础判题和提交记录查询。项目展示一条完整的 Java 后端业务链路，适合学习和面试演示。

> 判题模块仅供学习：用户代码由本机 JVM 子进程执行，不具备生产级安全隔离。请阅读[项目限制](#项目限制)，不要直接在存有敏感数据的服务器运行不可信代码。

## 技术栈

**后端：** Java 17、Spring Boot 4、MyBatis、MySQL、JWT（jjwt）、BCrypt、Jakarta Validation。

**前端：** HTML、CSS、原生 JavaScript。

**构建与测试：** Maven Wrapper、JUnit 5、MockMvc。

## 项目架构

```text
浏览器（HTML / CSS / JavaScript）
                │ HTTP JSON + Bearer Token
                ▼
          Controller
                ↓
            Service ─────────→ Judge 模块（javac / java 子进程）
                ↓
             Mapper
                ↓
              MySQL
```

Controller 接收请求并组织 HTTP 响应；Service 执行业务规则和状态流转；Mapper 使用 MyBatis 访问数据库；Judge 模块负责编译、运行代码并计算判题结果。成功与失败接口统一使用 `Result<T>`：`{ "code": 200, "message": "success", "data": ... }`。

## 核心功能

1. **用户认证：** 注册、登录、BCrypt 密码哈希和 JWT 身份认证。
2. **题目浏览：** 查询题目列表和题目详情。
3. **代码提交：** 创建 Submission，支持查询个人提交列表和详情。
4. **基础判题：** 状态由 `PENDING → RUNNING → FINAL` 推进，支持 `AC`、`WA`、`CE`、`RE`、`TLE`、`SE`。

提交处理过程：

```text
用户提交代码
      ↓
保存 Submission（PENDING）
      ↓
进入判题（RUNNING）
      ↓
编译并运行 Java 程序，比较题目样例输出
      ↓
保存最终状态、耗时和判题信息
      ↓
接口返回结果，前端展示
```

## 项目亮点

- JWT 拦截器保护用户和提交接口。
- `Result<T>` 统一接口响应，全局异常处理统一错误 JSON。
- Controller、Service、Mapper 分层，MyBatis 参数化数据访问。
- Submission 判题状态流转明确，先保存提交再回写结果。
- 用户提交记录按当前用户范围查询；题目响应不返回内部测试数据字段。

## 启动项目

### 环境要求

- JDK 17
- MySQL 8
- Maven Wrapper（仓库已提供 `mvnw` 和 `mvnw.cmd`）
- 本机 `PATH` 可执行 `java` 和 `javac`，判题需要 JDK 而不只是 JRE

### 数据库准备

先创建 `codearena` 数据库。应用启动时会按顺序执行 `schema.sql` 和 `data.sql`：前者创建 `user`、`problem`、`submission` 表，后者幂等插入 A+B 示例题目。因此，首次运行不需要手动预建这三张表。

例如在 MySQL 客户端执行：

```sql
CREATE DATABASE codearena
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

然后按下方步骤配置数据库连接并启动应用。脚本使用 `CREATE TABLE IF NOT EXISTS`，不会删除现有表或数据；它也不会自动调整已存在表的字段定义。如果你已有同名表，请先备份并核对字段、索引和外键与 `schema.sql` 一致。不要将数据库文件或真实凭据提交到仓库。

### 配置并运行

数据库凭据和 JWT 签名密钥都通过环境变量提供，不要写入版本控制文件。JWT 密钥至少需要 32 个 UTF-8 字节。PowerShell 示例：

```powershell
$env:CODEARENA_DB_URL = 'jdbc:mysql://localhost:3306/codearena?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
$env:CODEARENA_DB_USERNAME = '你的数据库用户名'
$env:CODEARENA_DB_PASSWORD = '你的数据库密码'
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$env:CODEARENA_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
.\mvnw.cmd spring-boot:run
```

Linux / macOS 示例：

```bash
export CODEARENA_DB_URL='jdbc:mysql://localhost:3306/codearena?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
export CODEARENA_DB_USERNAME='你的数据库用户名'
export CODEARENA_DB_PASSWORD='你的数据库密码'
export CODEARENA_JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

PowerShell 示例会在当前终端生成随机 JWT 密钥；关闭终端后环境变量会消失。配置参考见仓库根目录 [`application-example.yml`](application-example.yml)，数据库和 JWT 密钥均不写在 YAML 文件中。应用启动后打开 <http://localhost:8080/login.html>。

运行自动化测试：

```bash
./mvnw test
```

Windows PowerShell 使用 `.\mvnw.cmd test`。

## 项目结构

```text
src/main/java/com/codearena/codearena/
├── config/       Spring MVC 与拦截器配置
├── controller/   HTTP 接口
├── dto/          请求和判题结果对象
├── entity/       数据实体、状态枚举、Result<T>
├── exception/    业务异常和全局异常处理
├── interceptor/  JWT 请求认证
├── mapper/       MyBatis Mapper 接口
├── service/      业务接口
│   └── imp1/     业务实现；JudgeServiceImpl 当前位于这里
└── utils/        JWT 工具

src/main/resources/
├── mapper/       MyBatis XML
├── static/       HTML/CSS/JavaScript 页面
├── application.properties
├── data.sql
└── schema.sql
```

Judge 目前作为 Service 层中的 `JudgeService` / `JudgeServiceImpl` 实现，没有单独的 `judge/` 包。为避免无意义的包迁移，没有改变现有架构。

## 项目限制

当前判题器是学习版本：使用 `ProcessBuilder` 启动 JVM 子进程，在本机临时目录编译运行，并比较题目样例输入输出。它不是完整在线评测系统，不支持多测试点评测，也不构成安全沙箱。

如果用于生产环境，至少需要 Docker 等容器隔离、CPU/内存/进程数/执行时间限制、文件系统与网络访问控制，以及可靠的安全沙箱和任务调度。不要将当前判题器直接暴露给公网使用。

## 许可证

本项目采用 [MIT License](LICENSE)。
