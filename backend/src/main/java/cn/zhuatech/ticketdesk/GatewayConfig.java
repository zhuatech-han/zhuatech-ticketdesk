// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 环境支付模式和外部配置；真实收款缺配置时启动失败，不自动降为模拟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class GatewayConfig {
  final String mode, url, key, webhook;

  /** 缺少安全域名或真实模式凭据即拒绝启动，不悄悄切换模拟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public GatewayConfig(
      @Value("${ticketdesk.payment-mode}") String mode,
      @Value("${ticketdesk.public-url}") String url,
      @Value("${ticketdesk.stripe-secret-key:}") String key,
      @Value("${ticketdesk.stripe-webhook-secret:}") String webhook,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
    Rules.check(java.util.Set.of("DEMO", "STRIPE").contains(mode), "PAYMENT_CONFIG_INVALID");
    var uri = URI.create(url);
    Rules.check(
        uri.getHost() != null
            && uri.getUserInfo() == null
            && uri.getQuery() == null
            && uri.getFragment() == null
            && java.util.Set.of("http", "https").contains(uri.getScheme()),
        "PAYMENT_CONFIG_INVALID");
    if (mode.equals("STRIPE"))
      Rules.check(
          secure
              && uri.getScheme().equals("https")
              && (key.startsWith("sk_test_") || key.startsWith("sk_live_"))
              && webhook.startsWith("whsec_"),
          "PAYMENT_CONFIG_INVALID");
    this.mode = mode;
    this.url = url.replaceAll("/$", "");
    this.key = key;
    this.webhook = webhook;
  }
}
