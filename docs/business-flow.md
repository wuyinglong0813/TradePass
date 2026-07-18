# 财源通（TradePass）整体业务流程

本文档根据当前仓库中的微信小程序页面、后端接口和业务服务整理，描述从用户登录、企业接入到合作关系、订单、合同及对账的端到端业务流程。

图中：

- 实线表示当前已有的主要业务流程。
- 虚线表示依赖第三方服务、人工审核或尚在规划中的能力。
- `ACTIVE`、`PENDING` 等大写内容为当前代码使用的业务状态。

## 整体业务流程图

```mermaid
flowchart TD
    subgraph LOGIN["一、账号登录与会话"]
        direction TB
        L1["用户打开微信小程序"] --> L2{"是否同意隐私协议"}
        L2 -- "否" --> L3["仅浏览登录引导"]
        L2 -- "是" --> L4{"本地是否有有效 Token"}
        L4 -- "否" --> L5["微信登录／手机号授权"]
        L5 --> L6["创建或匹配用户"]
        L6 --> L7["签发会话 Token"]
        L4 -- "是" --> L8["恢复本地会话"]
        L7 --> L9["加载用户、企业、成员、权限和待办"]
        L8 --> L9
    end

    subgraph COMPANY["二、企业接入"]
        direction TB
        C1{"是否已有 ACTIVE 企业成员身份"}

        C1 -- "没有：创建／认领企业" --> C2["搜索并选择企业"]
        C2 --> C3["同意认证相关协议"]
        C3 --> C4["提交企业名称、信用代码和法人信息"]
        C4 --> C5["绑定申请人<br/>LEGAL_CANDIDATE／PENDING"]
        C5 --> C6["法人实名认证"]
        C6 --> C7["人脸核验"]
        C7 --> C8["提交企业资料与电子章"]
        C8 -. "正式环境待接入" .-> C9["第三方认证／人工审核"]
        C9 -.-> C10["企业认证通过<br/>法人身份 ACTIVE"]

        C1 -- "没有：受邀加入企业" --> C11["输入成员邀请码<br/>24 小时、单次有效"]
        C11 --> C12["生成成员申请<br/>GUEST／PENDING"]
        C12 --> C13{"法人／管理员审批"}
        C13 -- "通过" --> C14["分配角色与权限<br/>成员 ACTIVE"]
        C13 -- "拒绝" --> C15["删除待审批申请"]

        C1 -- "已有" --> C16["进入当前企业"]
        C10 --> C16
        C14 --> C16
    end

    subgraph ORG["三、组织与权限管理"]
        direction TB
        O1["加载当前企业租户"]
        O1 --> O2["切换企业"]
        O1 --> O3["加载角色、权限和待办"]

        O3 --> O4["法人 LEGAL<br/>全部权限"]
        O3 --> O5["管理员 ADMIN<br/>成员、授权、企业与模板管理"]
        O3 --> O6["销售 SALES<br/>客户、销售订单、合同与对账"]
        O3 --> O7["采购 PURCHASER<br/>供应商、采购订单、合同与对账"]
        O3 --> O8["财务 FINANCE<br/>对账与财务查看"]

        O4 --> O9["邀请、审批和移除成员"]
        O5 --> O9
        O4 --> O10["创建自定义角色与权限"]
        O5 --> O10
    end

    subgraph PARTNER["四、合作企业关系"]
        direction TB
        P1["选择供应商／采购商视角"]
        P1 --> P2{"是否已有合作关系"}
        P2 -- "没有" --> P3["法人生成合作企业邀请码"]
        P3 --> P4["对方企业法人接受邀请"]
        P4 --> P5["建立 ACTIVE 客户／供应商关系"]
        P2 -- "已有" --> P5
        P5 --> P6["进入合作方详情"]
    end

    subgraph ORDER["五、订单与经营数据"]
        direction TB
        T0["当前来源：后端 API／演示数据"]
        T01["Excel 导入及后台修正"]
        T1["订单录入<br/>SALE／PURCHASE"]
        T2["订单状态 CONFIRMED"]
        T3["按企业和交易方向汇总"]
        T4["累计／年度／月度经营排行"]
        T5["合作方近 12 个月交易趋势"]
        T6["订单列表与金额汇总"]

        T0 --> T1
        T01 -. "规划中" .-> T1
        T1 --> T2
        T2 --> T3
        T3 --> T4
        T3 --> T5
        T3 --> T6
    end

    subgraph CONTRACT["六、合同签署与审批"]
        direction TB
        S1["管理合同分类和模板"]
        S2["拥有 contract_sign 权限的成员"]
        S3["选择合作方与合同模板"]
        S4["自动填充甲乙方、日期等字段"]
        S5["编辑商品、金额及合同条款"]
        S6["确认发起合同"]
        S7["合同状态 PENDING"]
        S8["对方企业收到合同待办"]
        S9["查看合同并审批"]
        S10["合同状态 ACTIVE<br/>合同生效／履约中"]
        S11["合同状态 REJECTED"]
        S12["合同中心、合同预览和履约查询"]
        S13["合同状态 COMPLETED"]

        S1 --> S3
        S2 --> S3
        S3 --> S4
        S4 --> S5
        S5 --> S6
        S6 --> S7
        S7 --> S8
        S8 --> S9
        S9 -- "同意签署" --> S10
        S9 -- "拒绝" --> S11
        S10 --> S12
        S11 --> S12
        S10 -. "当前无完成接口" .-> S13
    end

    subgraph RECON["七、对账闭环"]
        direction TB
        R0["当前实现停留在订单汇总<br/>统一展示“待获取发票”"]
        R1["按合作方与 SALE／PURCHASE 查询订单"]
        R2["形成应收／应付金额汇总"]
        R3["获取发票与结算数据"]
        R4["逐笔匹配订单和发票"]
        R5{"金额是否一致"}
        R6["已匹配／完成对账"]
        R7["生成差异并跟进处理"]
        R8["开票、收付款及财务闭环"]

        R1 --> R2
        R2 --> R3
        R0 -. "发票数据源待接入" .-> R3
        R3 --> R4
        R4 --> R5
        R5 -- "一致" --> R6
        R5 -- "不一致" --> R7
        R6 -. "规划中" .-> R8
        R7 -. "规划中" .-> R8
    end

    subgraph TODO["八、待办驱动"]
        direction LR
        D1["成员待审批"]
        D2["企业认证待完成"]
        D3["合同待审批"]
        D4["企业工作台待办"]

        D1 --> D4
        D2 --> D4
        D3 --> D4
    end

    L9 --> C1
    C16 --> O1
    O3 --> P1

    C12 --> D1
    C5 --> D2
    C8 --> D2

    P5 --> T1
    P6 --> T5
    P6 --> S2

    T2 --> T4
    T6 --> R1

    O4 --> S1
    O5 --> S1
    S7 --> D3
    S10 --> R1
```

## 关键业务状态

| 业务对象 | 当前状态流转 |
| --- | --- |
| 企业认证 | `NOT_SUBMITTED` → `PENDING` → `PENDING_REVIEW` → `VERIFIED` |
| 实名认证 | `NOT_STARTED` → `VERIFIED` |
| 人脸认证 | `NOT_STARTED` → `VERIFIED` |
| 电子章 | `NOT_UPLOADED` → `PENDING_REVIEW` → `UPLOADED` |
| 企业成员 | `PENDING` → `ACTIVE`；拒绝时删除申请 |
| 合作关系 | 创建或接受合作邀请后进入 `ACTIVE` |
| 订单 | 创建后进入 `CONFIRMED` |
| 合同 | `PENDING` → `ACTIVE` 或 `REJECTED`；界面预留 `COMPLETED` |

## 角色与主要权限

| 角色 | 主要业务能力 |
| --- | --- |
| 法人 `LEGAL` | 全部权限、企业认证、成员管理、合作企业邀请 |
| 管理员 `ADMIN` | 成员与授权管理、企业管理、电子章和合同模板管理 |
| 销售 `SALES` | 供应商视角、客户关系、销售订单、合同签署与对账 |
| 采购 `PURCHASER` | 采购商视角、采购订单、合同签署与对账 |
| 财务 `FINANCE` | 发票查看和对账 |
| 访客 `GUEST` | 无默认业务权限，等待管理员分配角色 |

## 当前实现边界

1. 实名、人脸、电子章和企业审核在生产环境尚未接入正式服务商。
2. 企业创建后，`LEGAL_CANDIDATE/PENDING` 到正式法人 `LEGAL/ACTIVE` 的审核回调尚未形成完整闭环。
3. 订单已有创建、查询、汇总和排行接口，小程序当前主要使用演示数据；Excel 导入和后台修正仍在规划中。
4. 合同目前支持 `PENDING → ACTIVE/REJECTED`，尚无履约完成操作。
5. 对账目前只有订单金额汇总，发票、收付款和差异处理尚未接入。
6. 合同审批接口当前主要校验合同是否为 `PENDING`，后续还应补充对方企业身份及审批权限校验。

## 主要实现位置

- 登录与会话：`backend/src/main/java/com/tradepass/service/AuthService.java`
- 企业、成员与角色：`backend/src/main/java/com/tradepass/service/CompanyService.java`
- 订单、合作方与合同：`backend/src/main/java/com/tradepass/service/TradeService.java`
- 首页排行：`backend/src/main/java/com/tradepass/service/RankingService.java`
- 小程序页面：`miniprogram/pages/`
