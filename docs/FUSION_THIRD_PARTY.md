# 第三方附魔：新融合载体分类

> 这是新装载校验，不表示第三方附魔的重设计效果已经逐项完成。旧物品不会因为缺少分类被删除、改写或停用；单附魔跨种不使用本校验。

第三方附魔在**新装到装备、直接融合到装备、升级已有装备融合或翻转装备融合**之前，需要由数据包为该附魔显式提供合法的 `trigger`、`payload` 和非空 `fusion_hosts`。仅靠原版可附魔物品、物品槽位推测，或只添加触发/效果标签，不足以通过新装载检查。可以先制作、持有、翻转或升级融合书；真正装到装备时才检查。未配置时界面会提示需要分类，不会悄悄改成单附魔跨种。

例如在数据包的 `data/enchantment_fusion/enchantment_fusion/matrix_profiles.json` 中添加：

```json
{
  "profiles": {
    "example:storm_edge": {
      "trigger": "ON_MELEE_STRIKE",
      "payload": "VOLT",
      "fusion_hosts": ["SWORD", "AXE", "SPEAR"]
    }
  }
}
```

这里的 `fusion_hosts` 是**该附魔参与融合时允许的装备动作/载体**，不是该附魔的原版适用对象。原版附魔不按彼此的原版物品范围求交；两者均为第三方时，装备须满足两份显式融合载体声明。某个物品如果同时具有多个标准能力（例如被数据包同时标为剑和镐），任一匹配能力即可满足该附魔的声明。当前可声明的类别为：

`SWORD`、`AXE`、`SPEAR`、`MACE`、`TRIDENT`、`BOW`、`CROSSBOW`、`PICKAXE`、`SHOVEL`、`HOE`、`SHEARS`、`BRUSH`、`FLINT_AND_STEEL`、`FISHING_ROD`、`RIDING_ROD`、`SHIELD`、`HEAD_ARMOR`、`CHEST_ARMOR`、`LEG_ARMOR`、`FOOT_ARMOR`、`ELYTRA`、`OTHER_EQUIPMENT`。

这里的 `OTHER_EQUIPMENT` 只代表未被其他类别识别、且符合融合容器规则的装备；它不是允许所有物品或食物。带有标准物品标签的第三方剑、斧、矛和工具会按对应能力识别。类别未知、重复或数据类型错误会拒绝该数据包层的完整配置，不会只应用一部分。允许的触发与效果类型见 `TriggerType` 和 `PayloadType`；配置界面可编辑数据包已声明的 `fusion_hosts` 数组。

服务端为准，矩阵协议第 3 版同步分类给客户端预览；新客户端也能读取第 2 版矩阵，但旧协议没有载体声明，会保守地视第三方为未分类。旧客户端无法理解第 3 版矩阵，请服务端和客户端使用同版本以获得准确预览；服务器执行前仍会再次校验。分类不会追溯检查或删除旧装备数据。

局限：现有 `trigger` 和 `payload` 仍只是旧矩阵的粗粒度语义，分类只保证新装载经过显式授权；并不证明每个第三方融合有了符合新设计的独立动作、对象、资源结算或游戏验证。完整逐项实现仍见 [实现状态](FUSION_IMPLEMENTATION_STATUS.md)。
