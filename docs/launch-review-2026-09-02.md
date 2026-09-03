# 商签通上线前检查报告

> 本文保留修复前的审查证据。第 1–8 项均已完成代码修复（第 5 项在用户追加授权后关闭），最新结果见 [修复记录](launch-fixes-2026-09-02.md)。

检查日期：2026-09-02  
代码基线：`19bd8b5`；检查开始时工作区无未提交修改。

**结论：建议先完成下述 P1 修复，再提交正式上线。** 当前已有随机会话令牌、企业成员校验、权限模型、私有文件存储、文件摘要校验和数据库迁移机制，但合同签署、合同版本与退货库存仍有会改变真实业务结果的问题。

本次是代码审查与本地验证，没有修改业务源码，也没有调用线上合同签署、认证或库存写入接口。

## 检查范围与验证结果

检查覆盖登录会话与租户选择、企业创建及认证、角色与邀请、合同发起及签署、第三方回调、销售/退货单、库存与对账、项目账套、附件与物流文件、生产配置、小程序登录与服务页面。对关键写入路径进行了重点追踪，并非所有页面都经过真机操作。

| 检查 | 结果 |
| --- | --- |
| 小程序现有测试 `npm test` | 49 / 49 通过 |
| 后端现有测试 | 186 项，失败 0、错误 0、跳过 0 |
| 完整构建门禁 `mvn verify` | **失败**：JaCoCo 行覆盖率 57.66% ＜ 60%；分支覆盖率 44.38% ＜ 55% |
| JS 语法检查 | 40 个文件全部通过 |
| 页面注册与文件检查 | 29 个页面对应文件齐全，JSON 可解析 |
| 定向离线复现 | 复现 4 个问题，详见问题 1、2、3、8 |

离线复现使用实际业务方法，数据库与第三方网关使用 Mock，不代表已在生产环境执行过攻击或真实交易。原始记录：[Maven 日志](/tmp/tradepass-prelaunch-maven-20260902.log)、[小程序测试日志](/tmp/tradepass-prelaunch-mini-20260902.log)、[定向复现源码](/tmp/TradePassLaunchReviewProbe.java)、[定向复现结果](/tmp/tradepass-launch-review-probes.log)。

## 上线前需要修改的问题

### 1. P1：没有合同权限的用户也能先终止第三方签署任务

位置：[TradeController.java:262](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/controller/TradeController.java:262)、[FadadaContractSigningService.java:127](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaContractSigningService.java:127)。

撤回、拒绝接口先调用 `signingService.cancelPending(id)`，再执行包含合同归属及权限校验的 TradeService 方法。前一个方法直接按 ID 查询签署任务、调用法大大取消接口，没有验证当前用户、所属企业或签署权限。两个业务调用也不在同一个事务中。

**触发与影响：** 已登录但无权操作该合同的用户，向已创建签署任务的合同发送撤回/拒绝请求。最终响应虽然报无权操作，第三方任务已被终止，合法双方不能继续签署。

**验证：已离线复现。** 使用第三家企业身份，后续业务方法拒绝访问之前，网关取消方法已经被调用。

**修改建议：** 将权限、合同归属、当前状态和版本检查放在任何第三方写操作之前；在服务层提供统一撤回/拒绝编排，并处理第三方成功、本地持久化失败的补偿。补充“无权请求绝不调用网关”的回归测试。

### 2. P1：旧版签署回调可把新版合同标记生效，并串用归档文件

位置：[TradeService.java:580](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/TradeService.java:580)、[FadadaContractSigningService.java:116](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaContractSigningService.java:116)、[FadadaContractSigningService.java:225](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaContractSigningService.java:225)。

`resubmitContract` 允许修改 PENDING 合同并递增版本，但没有撤销旧签署任务。`syncBySignTaskId` 根据历史任务找到合同后，直接加载当前合同；后续没有比对任务版本与合同版本，使用当前版本元数据归档旧任务 PDF，再按合同 ID 激活。

**触发与影响：** V1 已生成签署链接；发起方把条款或金额改为 V2；双方完成旧链接签署或 V1 完成回调延迟到达。系统可能显示 V2 已生效，但归档 PDF 是 V1，合同页面金额与签署证据不一致。

**验证：已离线复现。** V1 回调调用了“以 V2 元数据归档 V1 文件”和“激活当前合同”。

**修改建议：** 签署任务严格绑定不可变版本快照；版本修改需先按明确流程终止旧任务；回调只能影响匹配版本；生效操作使用 ID、版本及待签状态的条件更新。重试和延迟回调不能跨版本生效。

### 3. P1：同一商品分多行退货，会少扣库存且可能超量退货

位置：[SalesOrderInventoryService.java:781](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/SalesOrderInventoryService.java:781)、[SalesOrderInventoryService.java:818](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/SalesOrderInventoryService.java:818)。

`createReturnTransfer` 先逐行读取并保存库存快照，再逐行用快照计算余额。同一商品出现两行时，两次读取都发生在扣减前；每行独立判断库存充足，随后第二次更新覆盖第一次扣减。

**例子：** 同一仓库现有某商品 10 件，退货单两行各退 6 件。两行均通过“6 ≤ 10”检查，最终两次把余额写成 4；出库流水合计为 -12，后续收货入库按两行累计。

**验证：已离线复现。** 扣减写入结果为 `[4, 4]`，没有因合计超库存而拒绝。

**修改建议：** 先按企业、仓库、商品 ID 汇总数量，再按固定顺序锁库存、验证总数量、原子扣减；单据明细可以保留多行。加入重复商品行、库存刚好够、合计不足三种测试。

### 4. P1：单据撤回与确认并发时可能覆盖状态，库存和对账仍保留

位置：[BusinessDocumentService.java:320](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/BusinessDocumentService.java:320)、[SalesOrderInventoryService.java:301](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/SalesOrderInventoryService.java:301)。

撤回先查询 ISSUED 单据，随后使用 `updateById` 写 WITHDRAWN 和删除时间；接收也先读后用 `updateById` 写确认/入库结果。双方没有共享行锁，也没有在最终更新中约束原状态。

**触发与影响：** 发起方撤回、对方确认几乎同时发生。两边先读到 ISSUED 后，对方完成入库及对账；撤回随后覆盖单据状态。结果可能是单据被隐藏，但库存、签名和对账记录已生成。另一执行顺序也会造成状态和删除标记矛盾。

**验证：代码路径与 SQL 条件确认；本轮未进行真实数据库并发压测。**

**修改建议：** 在事务开始时对同一业务单据加行锁，或统一使用状态条件更新并检查受影响行数；状态成功变更后再执行库存、签名、台账操作，保证这些数据库写入处于同一事务。补充双连接并发集成测试。

### 5. P1：prod 默认仍开启体验账号特权初始化

位置：[application-prod.yml:6](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/resources/application-prod.yml:6)、[ExperienceTestAccountService.java:65](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/ExperienceTestAccountService.java:65)。

生产配置 `TRADEPASS_EXPERIENCE_TEST_ACCOUNTS_ENABLED` 的默认值为 true。指定测试手机号完成验证后，会创建/更新两家测试企业，把企业认证、实名、人脸状态标为通过，并把指定账号设为 ACTIVE 法人。

**影响：** 若生产环境没有显式覆盖变量，体验数据和特权初始化会继续运行；这不等于真实服务商认证。关闭开关也不会撤销已经写入的测试企业及角色。

**修改建议：** 正式生产默认 false，并在发布配置显式关闭；核查、隔离既有体验数据及角色，避免其参与真实业务。保留独立体验环境。实际线上是否已覆盖变量，本轮未查控制台。

### 6. P1：企业认证通过后，创建者仍可直接改名称及法人姓名而保留认证状态

位置：[CompanyService.java:125](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/CompanyService.java:125)、[FadadaCompanyService.java:180](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaCompanyService.java:180)。

`submitCompany` 按统一社会信用代码找到已有企业后，只检查 `createdBy`，随后覆盖名称和法定代表人姓名等资料。对已认证企业没有冻结认证字段、重新认证或当前法人权限检查，原 VERIFIED 状态及已有服务商身份仍保留。`requireVerified` 只看缓存认证状态和授权范围，不比较修改后的名称。

**触发与影响：** 已认证企业创建者使用相同信用代码提交不同名称/法人姓名，平台继续把新资料显示为已认证；随后生成的业务文本可能与服务商认证主体不一致。

**验证：代码路径确认。**

**修改建议：** 普通资料编辑与认证主体变更分开；已认证名称、信用代码、法人信息只能通过重新核验流程更新，必要时冻结新签约；操作人需具备当前有效管理权限。

### 7. P1：多企业法人接受合作邀请时，可能绑定错企业

位置：[CompanyService.java:229](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/CompanyService.java:229)。

接受合作邀请时，后端查询当前用户任意一家 ACTIVE 法人企业并 `LIMIT 1`，未限制 `AuthContext` 中的当前企业。

**触发与影响：** 用户同时为 A、B 两家企业法人，在 B 企业页面接受 C 的邀请，实际关系可能建立在 A 与 C 之间，B 仍看不到合作关系。

**验证：查询条件确认；本轮未进行双企业真机验收。**

**修改建议：** 明确使用当前企业 ID，校验用户是这家企业的法人，防止自我合作；邀请码消费使用条件更新，不能吞掉任意数据库异常后仍返回成功。

### 8. P1：签署/认证回调处理失败后，缺少可靠恢复机制

位置：[FadadaCallbackService.java:47](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaCallbackService.java:47)、[FadadaCallbackProcessor.java:69](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/service/FadadaCallbackProcessor.java:69)。

事件存在即返回，不区分 PROCESSED、FAILED、RECEIVED；异步处理异常只将事件标为 FAILED。表内保存了摘要，但没有可恢复处理所需的完整事件或持久化任务。未发现扫描失败/卡住事件的补偿任务。

**触发与影响：** 已确认收取回调后，COS 下载、数据库或服务商查询短暂失败；相同事件再次投递仍被忽略。实例重启也可能留下未完成的 RECEIVED 事件。用户手动同步有机会修复状态，但无人操作时无法保证签署生效和归档最终完成。

**验证：已离线复现。** 对已有 FAILED 事件发送有效签名的同一事件，处理器未被重新调用。

**修改建议：** 持久化必要业务标识与可重试任务，成功后才按完成状态去重；对失败和超时处理中事件进行退避重试、租约回收，并提供告警和人工重放。处理本身必须幂等。

## 建议一并处理的体验问题

- **P2：成员移除后的会话恢复。** [AuthInterceptor.java:43](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/config/AuthInterceptor.java:43) 会拒绝失效的当前企业头并返回 403；[request.js:43](/Users/wuyinglong/IdeaProjects/wutest/TradePass/miniprogram/utils/request.js:43) 只自动处理 401。冷启动仍带已移除企业 ID，`/me` 无法完成恢复。建议仅针对“当前企业不可用”的明确错误，清理企业选择并重新拉取有效企业，保留仍有效的账号登录。
- **P2：正式版仍展示不能用的短信入口。** [login.wxml:33](/Users/wuyinglong/IdeaProjects/wutest/TradePass/miniprogram/pages/login/login.wxml:33) 展示“输入手机号登录 / 注册”，而 [login.js:49](/Users/wuyinglong/IdeaProjects/wutest/TradePass/miniprogram/pages/login/login.js:49) 在非开发环境只弹“服务暂未开放”。上线应隐藏入口或完成真实短信链路。
- **维护建议：** 合同预览页面约 2100 行，库存服务约 967 行，合同服务约 921 行。先修复状态、权限和版本问题，再按职责拆分公共校验与流程，降低以后局部修改漏掉另一条入口的概率。

## 发布前还需要核实的配置与运行条件

这些是待验收项，不能仅凭仓库判断已经配置正确。

| 项目 | 现有证据与需要确认的内容 |
| --- | --- |
| 发布门禁 | 本次 `mvn verify` 未通过。Dockerfile 使用 `-DskipTests package`，本身不会执行覆盖率门禁；发布流水线应先跑完整 verify 及 npm test，重点补充上述业务场景，不能只降低阈值。 |
| 登录可信来源 | [AuthController.java:39](/Users/wuyinglong/IdeaProjects/wutest/TradePass/backend/src/main/java/com/tradepass/controller/AuthController.java:39) 把 `x-wx-openid` 直接交给业务层，代码没有额外校验来源/AppID；必须核实生产入口对外部伪造身份头的过滤和源站访问限制。当前未验证公网可伪造成功，因此不将其写成已确认的线上漏洞。上传及服务商回调也依赖公网接口，不能简单关闭整个公网入口。 |
| 真机上传和认证页面 | 普通请求走 callContainer，但 [fileTransfer.js:146](/Users/wuyinglong/IdeaProjects/wutest/TradePass/miniprogram/utils/fileTransfer.js:146) 仍通过公网域名调用 wx.uploadFile；需在关闭调试/域名豁免条件下验证大文件上传、手写签名提交，以及服务商页面及跳转链路。两份项目配置目前都关闭了开发工具 URL 校验，模拟器成功不足以证明正式版可用。 |
| 真实认证和签署 | 核对生产 FADADA 配置、回调地址和授权范围；用两个独立企业完成个人认证、企业认证、印章、双方签约、下载签后 PDF、作废全链路，确认 PDF 版本、页面金额和签章一致。 |
| 历史数据导出 | Git 已跟踪 exports 下 3 份数据库导出，包含 sys_user、company、trade_contract 等表的 INSERT 数据，其中部分含 auth_session。未展示其内容，也未确认均为真实或均为测试数据。上线前确认脱敏与访问范围；真实备份应进入受控存储。仅添加 gitignore 不会清除已经跟踪的文件或历史。 |
| 数据恢复与监控 | 需核实数据库自动备份、恢复演练、COS 权限/保留、错误告警、资源容量。现有 `/tcb_probe` 恒定返回 200，不能证明数据库或电子签服务可用；另设数据库就绪检查及关键外部服务监控。 |
| 隐私与客服 | 页面自有协议勾选不等于已配置平台隐私指引；按实际使用的手机号、文件、拍照/选图、保存图片能力核实平台配置，并确认客服入口有人处理注销申请。没有自定义隐私 API 并不自动代表有 bug，微信可提供统一授权弹窗。[腾讯云官方适配说明](https://cloud.tencent.com/document/product/1301/97930)。 |
| 依赖维护 | 后端使用 Spring Boot 3.3.6。Spring 官方已说明 Spring Framework 6.1 开源支持结束，建议在隔离分支升级到仍受支持的版本并核查依赖漏洞。未因版本旧就断言项目可被某个 CVE 利用，需结合实际依赖及触发配置判断。[Spring 官方说明](https://spring.io/blog/2025/09/15/spring-framework-and-spring-security-fixes-for-CVE-2025-41249-and-CVE-2025-41248/)。 |

## 建议执行顺序

1. 先修复合同权限、版本隔离与认证字段保护，关闭生产体验初始化。
2. 修复库存重复行与单据并发状态、多企业邀请，补足失败回调恢复。
3. 用真实 MySQL 集成测试覆盖事务、唯一约束、并发与幂等；完整发布门禁通过。
4. 在体验环境关闭调试豁免，执行两个企业的真机交易和故障恢复验收。
5. 核实生产配置、备份、告警、隐私配置及客服后，再提交正式审核。
