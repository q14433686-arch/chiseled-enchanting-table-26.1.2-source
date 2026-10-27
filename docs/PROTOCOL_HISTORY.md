# 26.1.2 线协议修订史（enchanting wire revisions）

> **单一事实源**：`menu/EnchantingProtocol.java`（`CURRENT_REVISION_NUMBER`）。
> 每次 bump 都会改变通道标识（`…_26_1_2_revision_N`），新旧修订对端在加入时被友好断开
> 而非解码崩溃。历史修订通道以 **query-only 探针** 保留（cost 表自 revision_1、
> apply 通道自 revision_9），服务端仅据此判定对端版本并给出升级提示，绝不消费旧负载。
> 本文件由代码注释与发布报告汇总；早期修订语义以当时的线格式变更点为准。

| 修订 | 变更要点（线/blockstate schema） |
|---|---|
| 1–8 | 早期线协议（附魔成本表 S2C 同步基线；无 apply 修订体系） |
| 9 | 引入修订化 apply 通道 + 负载边界校验（batch 3：wire revision_9） |
| 10 | 1.1.0 前基线（成本/规则同步完整化） |
| 11 | Tome 页锁定（tome page locks）线字段 |
| 12 | XP 标尺与分级 grit 成本字段（十项实机反馈修复） |
| 13 | 附魔风味（ench flavor）同步字段 |
| 14 | 自定义成本选项 + 世代（generation）字段 |
| 15 | 书架品质（shelf qualities）字段 |
| 16 | TomeState 完整状态同步 |
| 17 | 书架能量（shelf power）服务器权威同步 |
| 18 | **`rainbow_cloth` 方块状态属性加入已同步 blockstate schema**（26.3 彩虹布全量回移植）；无其他线格式变化，仍须按门禁策略 bump |
| 19 | **`ApplyEnchantmentPayload` 严格材料方案索引 `cost_option_index`、`SkipRitualPayload.Action`（献祭/燃料加速/催化剂瞬发/强冲/安全取消）与 `TomePageActionPayload.Action`（主动修补/催化剂祝圣/母本信标定位）** |

## Bump 策略

- 任何 wire 字段或**已同步 blockstate 属性**的增删/重排都必须 bump，并保留全部历史探针。
- 仅服务端内部逻辑、仅客户端展示、仅语言/纹理的变更**不** bump。
- 姐妹线协议独立编号：26.2 = `26_2_revision_6`，26.3 = `26_3_revision_12`（三方通道互斥）。
