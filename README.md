# 哨兵 (Sentinel)

AI 用量监控 Android 应用，支持 **DeepSeek** 和 **MiMo** 双平台。

## 功能

- **Dashboard** — 余额、Token 消耗、请求次数、每日柱状图
- **Analytics** — 趋势图、模型饼图、缓存命中率、余额预估
- **双平台** — DeepSeek (API/Platform) + MiMo (小米 MiMo 平台)
- **桌面小组件** — 余额/消耗快速查看
- **多语言** — 中/英/日/韩/德/法/西/意/俄/土/越/繁中

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
- [MiMo-Tracker](https://github.com/TheMoDev/MiMo-Tracker) by TheMoDev — MiMo 平台数据抓取参考

## 包名

`com.flow.api` (namespace: `com.deepseek.lzjc`)
