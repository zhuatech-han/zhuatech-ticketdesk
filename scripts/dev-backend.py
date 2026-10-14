#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""JDK21源码调试启动，私有变量只传给子进程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

import os
import subprocess
from http_client import ROOT, env

values = env()
port = int(values.get("MYSQL_DEV_PORT", "33160"))
assert 1 <= port <= 65535
child = os.environ.copy()
child.update(values)
child["DATABASE_URL"] = (
    f"jdbc:mariadb://127.0.0.1:{port}/zhuatech_ticketdesk?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowLocalInfile=false&sslMode=trust"
)
child["DATABASE_USER"] = "ticketdesk"
subprocess.run(
    ["mvn", "-B", "-f", str(ROOT / "backend/pom.xml"), "spring-boot:run"],
    env=child,
    check=True,
    cwd=ROOT,
)
