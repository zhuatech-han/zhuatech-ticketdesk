# 部署、升级与备份恢复

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

需要Docker Engine/Desktop及Compose v2、Python3.10+，建议至少4GB可用内存。运行python3 scripts/init-env.py，检查.env及README配置，再docker compose -p ticketdesk up --build -d --wait --wait-timeout 1800。访问默认http://127.0.0.1:8160；/actuator/health和/actuator/health/readiness由Nginx同源代理。

.env.example只列字段及安全说明，真实凭据不提交。WEB_PORT和BIND_ADDRESS可覆盖，改端口同时改PUBLIC_URL；生产域名应配置HTTPS、COOKIE_SECURE=true、正确公网PUBLIC_URL及备份。不要停止别的项目来抢端口。官方镜像首次初始化可能较慢，先读本项目日志，不跳过测试。

STRIPE要求收款服务支持地区的商户、服务端密钥及webhook端点；[官方地区清单](https://stripe.com/global)未列中国大陆。连接/api/payments/stripe/webhook，订阅checkout.session.completed及checkout.session.async_payment_succeeded，按[Stripe回调](https://docs.stripe.com/webhooks)核对实际版本与合同。适配器只使用托管Checkout卡支付，未集成微信、支付宝或多商户结算。

启用邮件需MAIL_ENABLED=true及有效SMTP参数、MAIL_FROM；TLS及超时已配置。真实SMTP投递、真实银行收退款和实体手机摄像头必须独立验收。默认DEMO不执行银行操作，默认邮件禁用。

MySQL使用命名持久卷。首次空库执行V1身份、V2票务内核、V3活动与队列迁移，JPA持续校验；启动只初始化管理员、岗位、目录、类别和参数，不插入虚构活动或客户。重启不覆盖账号密码。追加新版本SQL，不改已执行迁移；升级先备份并在独立目标验证。

内置库备份：python3 scripts/backup.py --project ticketdesk --output private-backups/ticketdesk.zip。脚本短暂停止本项目后端写入、生成一致私有数据库ZIP并校验散列；备份含密码散列，未加密，需私有权限和额外异地加密。票签名密钥与环境由部署者另行安全保存。

隔离恢复：准备独立私有环境文件，保持原票密钥，设置空闲WEB_PORT、BIND_ADDRESS=127.0.0.1、PAYMENT_MODE=DEMO、MAIL_ENABLED=false、SCHEDULER_ENABLED=false。运行python3 scripts/restore.py private-backups/ticketdesk.zip --project ticketdesk-restore --env-file /private/path/restore.env。脚本拒绝已有项目/卷/网络/端口，校验ZIP成员和散列，只接收可信本地SQL（≤512MiB）。恢复后核对业务、权限和票码，不能直接启用外部任务或将测试副本作为另一个售票站点。

仅源码调试可叠加compose.dev.yaml暴露127.0.0.1数据库33160，再python3 scripts/dev-backend.py及frontend/npm run dev；需要本机Java21/Maven3.9/Node24.19。Vite代理8080，仅供调试。Docker启动无需本机Java/Node。

502先等后端健康；密码变更或重启后重新登录；健康失败读mysql/backend日志和私有配置；下载失败处理官方源网络再重试，不跳过测试。不能把更改.env当成重置已有管理员密码。
