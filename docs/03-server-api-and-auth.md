# Qoder 桌面版逆向分析（三）：服务端接口与认证

> 依据：主进程产物 `out/main/index.js`（prettier 还原后 19.5MB）中的字面量与路径常量统计。全库共 123 个 `/api` 端点字面量，其中 155 处调用集中在协作（collaboration）分组。

## 1. 网关与服务划分

服务端点按域名区分职责。openapi.qoder.sh 与 openapi.qoder.com.cn 承担开放接口；api2.qoder.sh 与 api.qoder.com 承担主体业务（协作与用户）；center.qoder.sh 承担账号与中心化能力；gateway.qoder.com.cn 为企业侧网关；mobile.qoder.com 对接移动端；download.qoder.com(.cn) 提供客户端分发。企业私有部署以 `acme.vpc.qoder.com.cn` 形式给出 VPC 端点示例，客户端支持保存该端点。

可观测性方面存在 `btardsb9ml-default-sea.rum.aliyuncs.com` 的 RUM 上报端点；模型侧存在 `dashscope.aliyuncs.com` 通道。

## 2. 认证流程

认证以 OAuth 与设备流组合实现，字符串中出现 oauth、oauth2、oauth_callback、token_exchange、device_flow、login-success、signing-in、signing-out、refresh_failed 等标识，并含 refresh_token、refresh_token_expires_in、refresh_token_expires_at、refresh_token_expire_time 等完整生命周期字段。

可确认的端点有四个。`POST /api/v1/deviceToken/poll` 用于设备授权轮询（对应 device flow，客户端展示设备码后轮询直到用户完成授权）。`POST /api/v1/deviceToken/refresh` 用于刷新设备令牌。`POST /api/v1/jobToken/exchange` 与 `POST /api/v1/jobToken/refresh` 是作业令牌的交换与续期，另有 `GET /api/v1/me/jobToken` 查询当前作业令牌。

这套组合的含义是：长期凭据（refresh token）换取设备令牌，再用设备令牌换取短期的作业令牌，模型与工具调用管道使用作业令牌鉴权。这解释了为何客户端同时存在 deviceId、device_token、jobToken 三套标识。

身份查询走 `GET /api/v1/userinfo`，数据策略走 `GET /api/v1/me/data-policy`。

## 3. 协作域接口

协作域是客户端与服务端交互量最大的部分，围绕 discussion（协作会话）展开，可归纳为七组。

成员与代理配置组：`GET /api/v1/collaboration/agent-memberships/query`、`GET /api/v1/collaboration/agent-profiles/current`、`GET /api/v1/collaboration/agent-profiles/{profileId}/avatar`、`.../invocation-policy`、`.../workspaces`、`POST /api/v1/collaboration/avatars`、`GET /api/v1/collaboration/connections`。此处的 agent profile 说明 Qoder 把“AI 代理”建模为可配置的身份，带调用策略与工作区绑定。

会话生命周期组：`GET|POST /api/v1/collaboration/discussions`、`GET|PATCH|DELETE /api/v1/collaboration/discussions/{id}`、`GET /api/v1/collaboration/discussions/{id}/snapshot`、`.../sources`、`.../key-messages`、`.../issue-links`、`.../{projectId}/{issueId}`。snapshot 与 sources 说明服务端持有会话快照与引用来源。

消息组：`GET|POST /api/v1/collaboration/discussions/{id}/messages`、`POST .../messages/query`、`POST .../messages/{messageId}/key-mark`、`.../messages/{messageId}/reactions`。消息支持检索、标记为关键、表情回应。

成员与在线状态组：`.../members`、`.../members/human/{memberId}`、`.../members/team/{discussionTeamId}`、`.../members/{kind}/{memberId}`、`.../members/{kind}/{memberId}/invocation-check`、`.../members/{kind}/{memberId}/invocation-policy`、`POST .../member-presences/query`。成员既可以是人也可以是代理与团队，且有可用性检查与调用策略。

代理执行组：`.../agent-executions` 与 `.../agents/{memberId}/workspaces`，对应在协作会话里触发某个代理在自己的工作区执行任务。

邀请与共享组：`.../invitations`、`.../invitations/{invitationId}/revoke`、`POST .../discussion-invitations/accept`、`.../invitation-settings`、`.../share-access`、`.../share-invitations`。

文件与通知组：`.../files`、`.../files/{fileId}`、`.../files/{fileId}/complete`、`.../files/{fileId}/download-authorizations`、`GET /api/v1/collaboration/me/notifications`、`GET /api/v1/collaboration/events`。文件上传采用预签名式三步（创建、分片完成、申请下载授权）；事件流单独提供，配合客户端做实时刷新。

## 4. 配额与企业能力

配额走 `GET /api/v2/quota/usage`，当前用户的计划走 `GET /api/v2/user/plan`。企业侧存在 `POST /api/v1/partner_plan/authorize` 与 `.../revoke`，以及 `GET /api/v1/me/partner_plans`，对应企业席位或合作方计划的授权与回收。

## 5. 远程控制与语音

远程控制：`POST /api/v1/remote/qoder/remote-control/enable` 与 `GET /api/v1/remote/qoder/remote-control/status`，与移动端接管桌面会话的文案相呼应。

语音：`POST /api/v2/service/realtime-voice/aoq/sessions` 创建实时语音会话（aoq 即前面分析的 RTC SDK），`/api/v2/service/ws/asr` 为语音识别 WebSocket 通道。

## 6. 本地状态

已确认本地存在 SQLite 表 `cacheInterceptorV3`（body、deleteAt、statusCode、statusMessage、headers、etag、cacheControlDirectives），用于 HTTP 层缓存与失效。此外字符串中出现 `deviceMissingOrOccupied` 与 `refreshPolicy`，前者是设备绑定冲突的错误标识，后者是令牌刷新策略。

## 7. 复刻映射

上述接口中，协作域与配额、企业计划、远程控制均依赖 Qoder 服务端，开源复刻无法也不应直接对接。可以复刻的是客户端侧的结构：令牌分层与刷新策略、HTTP 缓存拦截层、会话与消息的本地模型、事件流驱动的 UI 刷新。复刻实现（qoder-open）目前提供模型协议与工具层，本地会话与缓存层将在下一阶段补齐。
