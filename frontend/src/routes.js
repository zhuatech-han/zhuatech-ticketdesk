// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 分开观众与工作台地址，避免同一个events路径混用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function routePath(section) {
  return section === "public"
    ? "/events"
    : section === "portal"
      ? "/wallet"
      : "/work/" + section;
}
/** 读取已知界面路由，票夹查询参数不改变岗位页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function routeSection(hash) {
  const path = hash.split("?")[0];
  if (path === "#/wallet") return "portal";
  if (path.startsWith("#/work/")) return path.slice(7);
  return "public";
}
