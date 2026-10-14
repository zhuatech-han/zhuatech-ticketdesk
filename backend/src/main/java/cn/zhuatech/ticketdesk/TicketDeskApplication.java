// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import java.time.Clock;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 活动票务系统启动及签名配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootApplication
@EnableScheduling
public class TicketDeskApplication {
  /** 启动Web服务；密码和票密钥只由环境注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(TicketDeskApplication.class, args);
  }

  /** 服务器统一时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** 票签名密钥必须稳定且至少256位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  TicketCodes codes(@Value("${ticketdesk.signing-key}") String key) {
    return new TicketCodes(Base64.getUrlDecoder().decode(key));
  }
}
