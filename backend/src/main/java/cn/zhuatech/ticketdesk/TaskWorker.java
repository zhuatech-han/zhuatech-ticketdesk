// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ticketdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时清理及独立事务任务执行；进程重启从数据库恢复待办。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class TaskWorker {
  final TaskService tasks;
  final Core core;
  final boolean enabled;

  public TaskWorker(
      TaskService tasks,
      Core core,
      @Value("${ticketdesk.scheduler-enabled:true}") boolean enabled) {
    this.tasks = tasks;
    this.core = core;
    this.enabled = enabled;
  }

  /** 有界后台执行，无外部通知或真实款项配置时不伪造结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(fixedDelay = 15000, initialDelay = 15000)
  public void tick() {
    if (!enabled) return;
    try {
      core.expire();
      var due = tasks.due();
      for (String id : due.get("refund")) {
        try {
          tasks.refund(id);
        } catch (Exception ignored) {
          tasks.failed("refund", id);
        }
      }
      for (String id : due.get("mail")) {
        try {
          tasks.mail(id);
        } catch (Exception ignored) {
          tasks.failed("mail", id);
        }
      }
    } catch (Exception ignored) {
    }
  }
}
