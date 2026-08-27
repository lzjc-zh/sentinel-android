# 哨兵 (Sentinel)

AI 用量监控 Android 应用，支持 **DeepSeek**、**MiMo** 和 **火山方舟** 三平台。

## 功能

- **Dashboard** — 余额/额度、Token 消耗、每日柱状图、点触查看模型详情
- **Analytics** — 趋势图、模型饼图、缓存命中率、余额预估
- **三平台** — DeepSeek (API/Platform) + MiMo (小米) + 火山方舟 (Agent Plan)
- **桌面小组件** — 余额/消耗快速查看
- **多语言** — 中/英/日/韩/德/法/西/意/俄/土/越/繁中

## 平台支持

| 平台 | 数据来源 | 功能 |
|------|----------|------|
| DeepSeek | API + Platform | 余额、Token 消耗、缓存命中率 |
| MiMo | 小米平台 | 余额、Token 消耗、缓存命中率 |
| 火山方舟 | Agent Plan API | 套餐信息、AFP 额度、Token 消耗 |

## 架构

- Kotlin + Jetpack Compose
- Hilt 依赖注入
- Room 数据库 (多平台隔离)
- Retrofit + OkHttp
- DataStore 偏好存储
- WorkManager 后台刷新

## 构建

```bash
# 需要 JDK 17 + Android SDK (compileSdk 35)
./gradlew assembleDebug
```

## 致谢

- [SeekFlow](https://github.com/DavidBlon/SeekFlow) by DavidBlon — DeepSeek 用量追踪原型

## 包名

`com.flow.api` (namespace: `com.deepseek.lzjc`)
