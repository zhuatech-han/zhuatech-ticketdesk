# API 与权限

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

所有请求同源，经Nginx代理/api。写入需先GET /api/auth/csrf，并把返回header/token作为请求头。仅POST /api/payments/stripe/webhook免CSRF，仍需Stripe-Signature原文验签。会话HttpOnly、SameSite Strict，HTTPS部署启用Secure。

|入口|用途与权限|
|---|---|
|GET /public/events、/public/events/{id}、/public/config|匿名活动、座位状态及非敏感模式|
|POST /auth/register、/auth/login；GET /auth/me|注册观众、登录、当前身份|
|POST /auth/password、/auth/logout|本人密码修改及退出|
|GET /catalog|具有工作台权限的岗位活动目录，仍过滤组织/活动|
|POST /events；PUT/DELETE /events/{id}|events权限、组织/活动范围；删除仅无历史草稿|
|POST /events/{id}/types；PUT/DELETE /events/{id}/types/{typeId}|events权限；草稿无历史才能修改|
|GET /events/{id}/layout|events权限的实际座位图|
|POST /events/{id}/reserve|portal权限、本人口径；seatId和规范UUID requestId|
|GET /wallet、/wallet/{id}|本人订单及票码，不能代理别人|
|POST /wallet/{id}/checkout、demo-pay、cancel、refund|本人流程；demo-pay仅DEMO模式|
|GET /events/{id}/operations、/finance|分别sales或refunds权限及活动范围；不返回票码|
|POST /events/{id}/check-in|checkin权限及活动授权；code≤128字符|
|POST /orders/{id}/refund、/tasks/{kind}/{id}/retry|refunds权限及原活动范围|
|GET /reports、/reports.csv|reports权限，分模式币种；CSV防公式注入|
|GET /audit|audit权限、组织范围，最近500条脱敏动作|
|GET/POST /admin/{kind}；PUT /admin/{kind}/{id}|users/roles/settings权限且ALL范围；固定目录仅更新|
|GET /admin-options|账号管理选项目录|

401表示登录或会话失效；403表示权限或岗位范围不足；409表示业务条件、状态或版本冲突；400表示输入格式；503表示外部支付不可用，结果可能未知。所有错误只返回安全代码，不回显密码、SQL或原始支付内容。字段与操作示例以Controller、业务手册和自动化脚本为准，不把软件内部接口直接当成无鉴权集成API。
