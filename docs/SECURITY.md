# 安全与配置

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

仅公开源码学习版，没有生产安全认证或容量保证。部署者承担HTTPS、补丁、监控、备份和业务隐私告知责任。

管理员密码、MySQL密码、票签名密钥、Stripe和SMTP凭据只从私有环境注入；.env及私有QA状态不提交。初始化生成独立强密码和256位票密钥，拒绝覆盖已有.env。票密钥须随私有备份单独保管；改换会影响已打印票码。不要在公开截图或Issue展示真实票码、客户资料、密码或接口凭据。

密码BCrypt12，登录失败窗口和注册限流在进程内，正式部署还应设置反代限流。仅本人查询票码；后台不返回密码散列或票码。部门、账号和密码指纹每次操作重新检查，权限不依赖前端菜单。没有邮箱验证、自助找回密码或跨设备离线核销。

出票与退款使用行锁及不可变金额；原始Stripe签名按时间窗验证，核对会话、币种、金额和live/test模式，用实际支付身份去重。缺真实模式必要配置拒绝启动，不悄悄退回模拟。仍未完成实际商户环境验收；应测试完整收退款及异常后再启用真实经营。

内置MySQL仅容器网络可达，驱动sslMode=trust仅适用此隔离网络；外部数据库使用verify-full及可信CA。Nginx同源代理、禁止嵌入、无敏感访问日志、浏览器内容策略；数据库和后端不开放宿主端口。

摄像头需[安全上下文和用户授权](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia)；请求ID使用[getRandomValues](https://developer.mozilla.org/en-US/docs/Web/API/Crypto/getRandomValues)，不以Math.random造身份。正式公共站点仍须HTTPS，不能因为HTTP下可手输票码就忽略登录信息保护。

源码与第三方原始许可分别适用，见LICENSE和THIRD_PARTY。敏感漏洞通过官网或联系知华科技私下反馈，不公开利用代码与业务数据。
