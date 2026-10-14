// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 时间表单按浏览器时区显示分钟，保留服务端原值另作比较。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localTime(value) {
  const d = new Date(value);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
/** 未修改的显示值保留原始秒和毫秒，避免误改被冻结业务规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eventTime(display, original) {
  return original != null && localTime(original) === display
    ? new Date(original).toISOString()
    : new Date(display).toISOString();
}
