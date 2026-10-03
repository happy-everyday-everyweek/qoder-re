# Qoder 桌面版逆向分析（四）：agent 配置模型与 MCP

> 依据：worker 产物中 agent 定义构造函数（prettier 还原后第 261930 至 262005 行区域）及其配套解析逻辑。

## 1. agent 配置模型的完整字段

Qoder 的 agent（自定义子代理，对应协作接口中的 agent profile）以一个字面量对象描述，字段如下。

标识类：kind 固定为 "local"，name、description、color 用于展示。
 n提示与查询：promptConfig 包含 systemPrompt 与 query，其中 systemPrompt 来自配置文件的 system_prompt 字段，而 query 在此路径下为未定义。对于文件形式的 agent，系统提示则来自 prompt 字段的别名映射。

模型配置：modelConfig 包含 model 与可选的 generateContentConfig（至少可从 temperature 推导）。

运行限制：runConfig 含 maxTurns 与 maxTimeMinutes，两者仅在配置中至少提供其一时才生成，说明回合上限与墙钟超时是可选约束。

工具与外部服务：toolConfig.tools 列出允许使用的工具；mcpServers 为 MCP 服务器数组；disallowedTools 为黑名单；skills 为可用技能。

输入契约：inputConfig.inputSchema 默认给出 `{ type: "object", properties: { query: { type: "string", description: "The task for the agent." } }, required: [] }`，即子代理接收一个 query 字段。

行为控制：effort（推理强度）、initialPrompt（初始提示）、permissionMode（权限模式）、hooks（钩子）、memory（记忆开关）、background（后台运行）、isolation（隔离级别）。

## 2. MCP 服务器配置字段

MCP 服务器条目在解析时显式枚举了以下字段：http_url、headers、tcp、type、timeout、trust、description、include_tools、exclude_tools。

这组字段说明：Qoder 同时支持 HTTP 与 TCP 两种传输；支持按工具名白名单（include_tools）与黑名单（exclude_tools）裁剪 MCP 工具面；trust 与 description 属于对服务器的信任与展示描述；timeout 为调用超时。

## 3. 派生自 Gemini CLI 的证据

多处命名指向 qodercli 基于 Gemini CLI 派生。首先是上面出现的 generateContentConfig，这是 Google GenAI SDK 的配置命名。其次是环境变量集合中出现的 GEMINI_CLI_ACTIVITY_LOG_TARGET、GEMINI_CLI_MAX_MCP_OUTPUT_TOKENS、GEMINI_FORCE_ENCRYPTED_FILE_STORAGE。

同时，另外一些变量带有 Anthropic 与 OpenAI 的痕迹（anthropic、openai、openai-responses、openai-compatible、anthropic-compatible、systemPromptOverride、assistant_tool_use_continuation）。合理解释是：Qoder 以 Gemini CLI 的 agent 框架为基底，扩展出多协议模型适配层，并叠加了自有服务端能力（协作、代码库索引、配额）与安全插件。

## 4. 与复刻的对应关系

复刻实现（qoder-open）已实现的部分对应关系如下。工具配置对应 tools/registry.ts；运行限制对应 agent/loop.ts 的 maxTurns 与上下文阈值；输入契约对应 Task 工具的 parameters；MCP 服务器配置尚未实现，是下一项工作；技能对应 skills/loader.ts；权限模式对应 agent/permissions.ts 的策略对象；子代理的 background 与 isolation 尚未实现。

MCP 接入的设计已由上述字段确定：需要支持 http 与 tcp 传输、按 include_tools 与 exclude_tools 裁剪、把远端工具以 mcp__<server>__<tool> 形式注入注册表，并以 timeout 控制单次调用。
