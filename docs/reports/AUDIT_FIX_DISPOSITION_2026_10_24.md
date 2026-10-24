# 2026-10-24 双审计修复处置报告

来源:附件 `CET_26_3_BRANCH_AUDIT_ZH.md`(分支深度审计,452 行)+ 仓库 ZIP 审计站点
(`docs/mod-source-code-audit (1).zip`,35 条 finding)。
提交链:`e9775a0`(批1A)→ `8049bf0`/`ab07122`/`f6058b1`(批2)→ `605e48f`/`4126d0b`(批3)→ 本报告提交。
验证:GitHub `build`(静态门禁、gradle build、无 JEI/REI 编译、真实服务端启动+`/reload` smoke)
与 `compile-check`(含 JUnit)均为 success;沙箱无 JDK,本地仅跑四项 Python/shell 门禁。
真实客户端 GUI、多人联机、结构生成实测**未手工验证**(超出沙箱能力),仅 CI 证据。

## 文档审计(26.3 分支审计)

| 编号 | 处置 |
|---|---|
| C-01 | ✅ 完成步按实际物品复算(recordVersion 快照守卫 + 全部失效全退 XP/材料/青金石) |
| C-02 | ✅ 冲突退款按实际 XP 计算(原快照×实际比率,新建 `BookConversion` 权威换算) |
| H-01 | ✅ 合书按目标整本计价/扣费(step 纳入 materialPredicate + 服务端/客户端同查整本) |
| H-02 | ✅ 统一三路解析;新增 datapack 显式 `material_free`/`xp_only`;无规则与服务端总闸关闭均 fail-closed |
| H-03 | ✅ 合并条目数上限 2048 + 超限回退未合并束 |
| H-04 | ✅ `expandBundles` 递归展开(原一层 copy) |
| H-05 | ✅ 注册 3 个结构 Mixin + 删重复 AddRandomTick + CI 孤儿 Mixin 门禁 |
| H-06 | ✅ 新等级范围校验前移至任何消耗之前 |
| H-07 | ✅ 同步产物统一 volatile 不可变表整体发布 |
| H-08 | ✅ 仪式曲线归一化分母改为可配置并同步客户端 |
| H-09 | ✅ JEI/REI 刷新监听装配核查(单源监听) |
| M-01 | ✅ Widget 仅左键;右键保留给未来动作 |
| M-02 | ✅ 启发式候选改为严格 `>` |
| M-03 | ✅ `EmptySacrifice` 改判战斗附魔 |
| M-04 | ✅ widget 候选 `>`、共用复合规则拆分(见解析统一) |
| M-05 | ✅ `restrictVillagerInfinityBooks` 默认 true 并全局接线 |
| M-06 | ✅ `vanilla_chest_books_mode` 描述追加 /reload 提示(11 语) |
| M-07 | ✅ 铁砧合并 Mixin 改 `@WrapMethod` + try/finally |
| M-08 | ✅ 砂轮改走容器槽/`mayPickup` 代理(等价官方更新 javadoc) |
| R-01 | ✅ 成本表加字节估算预算 + 尾随 `costTableComplete`;客户端不完整表 fail-closed |
| R-02/R-03 | ✅ 队列上限、load quarantine、无主恢复 |
| R-04 | ✅ 协议探针收敛为单入口(见下 DEAD-03) |
| R-05 | ✅ costume 覆写 `getMenuProvider`(重开改走 vanilla 打开路径)+ Reopen 节流 |
| R-06 | ✅ 结构填充仅当原状态为 BOOKSHELF 而处理器无产出时填充 |
| L-* | ✅ 11 语成就描述去固定 -40%;tip_g3 去除固定批量数字;min 钳制;成就改配置达标判定 |

## ZIP 审计(35 条)

| 编号 | 处置 |
|---|---|
| MX-01 | ✅ = H-05(结构 Mixin 注册) |
| MX-02 | ✅ 删重复 AddRandomTick 入口(与 H-05 合并) |
| MX-03 | ✅ 砂轮 `mayPickup` 代理(= M-08) |
| MX-04 | ✅ 铁砧 `@WrapMethod` + try/finally(= M-07) |
| MX-05/MX-06 | ✅ / 延期:`defaultRequire:1` 为版本锁定移植的刻意 fail-fast,保留 |
| NET-01 | ✅ 8 个客户端发送点加 `canSend` 守卫 |
| NET-02 | ✅ wire → `26_3_revision_9`;apply/reroll/freeze/reopen 四业务通道后缀化;裸 ID 降为查询探针 |
| NET-03 | ✅ Apply 载荷 compact-constructor 统一校验 + token 有界读取 + 异常不回显输入 |
| NET-04 | ✅ 字节预算(= R-01) |
| DATA-01/02 | ✅ 仪式 NBT 守卫与队列上限(= R-02) |
| DATA-03/04 | ✅ 全部 `String.format` 钉 `Locale.ROOT` + CI 防回归门禁 |
| API-01 | ✅ icon 移至下划线命名空间 |
| API-02 | ✅ `fabric-api >=0.162.0` |
| API-03 | ✅ 禁用 JEI/REI 的 jar 在 processResources 剔除对应 entrypoint(no-viewers CI 已验) |
| API-04 | 延期:ModMenu 入口桩需独立设计评审 |
| GUI-01/02/03 | ✅ 模态契约显式化 + charTyped 转发;分类键按枚举长度;Grimoire 改 `Util.getMillis()` |
| DEAD-01 | ✅ postProcess* 经 H-05 注册后有生产调用点 |
| DEAD-02 | ✅ 删单参 `computeSacrificeXpLevels(long)` |
| DEAD-03 | ⚠️ 保守处置:rev4–8 探针**未退役**。删之会把旧版客户端误判为原版连接、迟到菜单解码才难看断线;改为收敛单入口 `peerHasAnyCostChannel` + 注释退役政策 |
| DEAD-04 | ✅ 删 `PayloadLimits.MAX_COLLECTION` 死别名 |
| COUP-01/02/03 | 延期:巨类拆分/静态耦合属架构代差,非本轮范围 |
| COUP-04 | ✅ DISCONNECT 补 `hasSingleplayerServer` 守卫,与 SYNC 路径对称 |
| BAL-01 | ✅ 归一化分母配置化(= H-08);6 个曲线系数未收进 Config(避免平衡漂移) |
| BAL-02 | 仅记录:分段上限冗余属调优决定,未改数值(成就判定已改按配置上限) |
| BAL-03~05 | 仅记录:数值/UI 外观级,非缺陷 |
| BAL-06 | 误判:42 为「有效书架」扫描计数,不受直附 38 上限约束,可达 |

## 刻意未改的明示规则(审计标注的设计取舍)

创造模式收据为 0、freeze/reroll 独立收费、取消不退费、母本磨损、XP 精确扣除、
provenance 100%/25%、quarantine 隔离、PayloadLimits 负数拒绝、原版同类惩罚三级回退 —— 保持不变。

## 待人工驗收

- 真实客户端:开启菜单、仪式全流程、铁砧/砂轮行为、Grimoire 渲染。
- 多人:revision_9 互联互通、混合版本失败体验、重开菜单节流手感。
- 世界生成:结构书架替换/填充在新区块的实际效果(MX-01 激活后首次生效,需目视确认一轮)。
