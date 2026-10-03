# Qoder 桌面版逆向分析（五）：主循环、loop 机制与上下文阈值

> 依据：worker 产物中回合计数、loop 注入与上下文窗口相关代码区域（prettier 还原后第 188430 附近、303230 附近、99418 与 149262 附近）。

## 1. 回合与上限

回合余量的计算方式是 `maxTurns - turnsUsed`，下限取 0；当 maxTurns 未配置时，余量取正无穷，并在遥测与日志中统一标记为 unbounded，同时把 max_turns 字段记为 none。这说明上游对“无限回合”是显式建模的，而不是缺省值为某个大数。

遥测字段中同时上报 max_turns 与 remaining_turns，且在多个事件分支中重复出现，可见回合消耗是运行时可观测性的核心指标之一。

## 2. loop 注入机制

存在名为 loop.md 的循环定义文件，内容长度上限为 8192 字符。注入到上下文时使用两个标记位：`<<loop.md>>` 与 `<<loop.md-dynamic>>`。前者对应静态的循环定义，后者对应需要每次重新渲染的动态部分。

结合斜杠命令的描述（`/loop --max-turns` 控制轮次上限、`stop: true` 结束循环、循环不会自行重新调度未改变的任务、需要用户显式要求才重新调度），可还原这套机制的使用方式：用户用 loop.md 描述周期性任务与唤醒节奏，运行时在首次触发与文件变化时注入其内容，后续未变化的触发只注入动态部分以节省上下文。

循环还涉及“事件驱动唤醒 + 坠落心跳”的双重保障：事件本身是唤醒信号，新的唤醒时间只是备用心跳。停止条件是任务完成或达到轮次上限。

## 3. 上下文窗口与压缩阈值

上下文窗口由模型自带或配置提供，代码中存在 contextWindow 为 1e6（100 万 token）的取值。窗口超出会计数（contextWindowExceededCount）并作为遥测指标上报（context_window_exceeded_count）。

压缩阈值并非完全由客户端决定：日志中出现 `[config-service] auto_compact_model_threshold_caps received`，即阈值上限由配置服务下发（按模型维度），客户端侧的 `auto_compact_model_threshold_caps` 与之对应。这与前面分析到的 COMPRESSION_FAILED_* 错误分类共同构成一套可远程调控的上下文策略。

## 4. 对复刻实现的映射

复刻实现（qoder-open）已具备回合上限（maxTurns，达到即停止）与上下文裁剪（trimToolOutputs + 估算阈值）。尚未实现的是两件事：一是把阈值改为可按模型覆写（对应配置服务下发的能力），二是 loop.md 的注入与动态渲染。这两项已进入路线图。
