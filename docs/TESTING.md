# 测试与验收

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

后端：mvn -B -f backend/pom.xml spotless:check test。前端在frontend执行npm ci --no-audit --no-fund、npm run format:check、npm run lint、npm test、npm run build。Docker构建亦执行测试，不使用skipTests。

37项后端（13事务并发、13HTTP身份/业务、3签名付款数据库流程、5网关合同、3票码规则），17项前端。支付合同测试使用明确假的QA凭据及模拟HTTP响应，真实验签、数据库状态和金额绑定被执行，但不代表Stripe真实商户验收。

真实MySQL补充：在独立非生产项目使用docker compose -p ticketdesk-tests -f compose.test.yaml run --rm backend-tests，完成后docker compose -p ticketdesk-tests -f compose.test.yaml down -v。该文件独立使用临时MySQL，不得与生产Compose合并。

完整DEMO空卷验收先按README启动，再python3 scripts/quality.py --base http://127.0.0.1:8160。脚本创建标记QA的200座活动及独立账号，执行实际HTTP登录、选座、恢复、出票、检票防重、退款队列、拒绝无权限及分模式报表；私有状态output/ticketdesk-quality-state.json为0600且不提交。仅可在独立DEMO库运行，不能用于真实经营库。

重启后python3 scripts/verify-persistence.py --state output/ticketdesk-quality-state.json。备份恢复到隔离目标后用--base目标地址复核，参见DEPLOYMENT。页面验收应检查桌面与窄屏、登录、用户活动、座位、票夹、检票、后台、报表和权限页面，并保留当前运行截图。二维码图片识别或手输不能代替实体手机摄像头、闸机、打印机或离线设备验收。

发布前python3 scripts/release-check.py及git diff --check，扫描真实敏感值，核对官网、LICENSE、双语、六类实拍截图及两个原始微信二维码。按实际结果记录，不把未执行检查记为通过。
