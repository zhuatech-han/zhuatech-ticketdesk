// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import jakarta.persistence.*;

/** 组织归属、数据库身份和显式乐观版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@MappedSuperclass
public abstract class RecordBase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long departmentId;

  @Column(nullable = false)
  public long version = 1;
}
