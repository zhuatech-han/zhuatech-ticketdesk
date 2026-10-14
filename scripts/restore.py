#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只恢复到全新独立内置数据库项目，不覆盖旧资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""

import argparse, hashlib, json, re, socket, subprocess, time, zipfile
from backup import ROOT, invoke, validate_env


def main():
    p = argparse.ArgumentParser()
    p.add_argument("backup")
    p.add_argument("--project", required=True)
    p.add_argument("--env-file", required=True)
    a = p.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,50}", a.project):
        p.error("Invalid Compose project")
    env = validate_env(a.env_file)
    if env.get("SCHEDULER_ENABLED") != "false" or env.get("MAIL_ENABLED", "false") != "false" or env.get("PAYMENT_MODE", "DEMO") != "DEMO":
        raise SystemExit("Isolated restore requires SCHEDULER_ENABLED=false, MAIL_ENABLED=false and PAYMENT_MODE=DEMO; never enable external actions before review")
    if env.get("BIND_ADDRESS", "127.0.0.1") not in ["127.0.0.1", "::1"]:
        raise SystemExit("Restore binds only to localhost until separately validated")
    prefix = ["docker", "compose", "--env-file", a.env_file, "-p", a.project]
    if invoke(prefix + ["ps", "-aq"], stdout=subprocess.PIPE).stdout.strip():
        raise SystemExit("Refuse to overwrite existing Compose project")
    for kind, name in [
        ("volume", a.project + "_mysql-data"),
        ("network", a.project + "_default"),
    ]:
        if (
            subprocess.run(
                ["docker", kind, "inspect", name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode
            == 0
        ):
            raise SystemExit("Refuse to overwrite existing resources")
    with zipfile.ZipFile(a.backup) as archive:
        if len(archive.namelist()) != 2 or set(archive.namelist()) != {
            "manifest.json",
            "database.sql",
        }:
            raise SystemExit("Invalid archive members")
        if any(member.file_size > 512 * 1024**2 for member in archive.infolist()):
            raise SystemExit("Archive member exceeds restore limit")
        manifest = json.loads(archive.read("manifest.json"))
        data = archive.read("database.sql")
        if manifest.get("product") != "TicketDesk" or manifest.get("version") != 1:
            raise SystemExit("Incompatible backup")
        if (
            manifest["database"]["size"] != len(data)
            or manifest["database"]["sha256"] != hashlib.sha256(data).hexdigest()
        ):
            raise SystemExit("Backup hash mismatch")
    # Reject a busy local port before creating any target resources.
    host = env.get("BIND_ADDRESS", "127.0.0.1")
    try:
        port = int(env.get("WEB_PORT", "8160"))
        if not 1 <= port <= 65535:
            raise ValueError()
        # Docker Desktop can reserve a port without a normal host socket listener.
        published = invoke(["docker", "ps", "--format", "{{.Ports}}"], stdout=subprocess.PIPE).stdout.decode()
        if re.search(r":" + str(port) + r"->", published):
            raise OSError("Port already published by a running container")
        with socket.socket(socket.AF_INET6 if host == "::1" else socket.AF_INET) as probe:
            probe.bind((host, port))
    except (OSError, ValueError):
        raise SystemExit("Restore port is invalid or occupied; choose another WEB_PORT in the target environment file")
    # Verify before creation. Only restore trusted local backups; SQL is not sandboxed.
    invoke(prefix + ["build"], stdout=subprocess.DEVNULL)
    invoke(prefix + ["up", "-d", "mysql"], stdout=subprocess.DEVNULL)
    ready = False
    for _ in range(900):
        test = subprocess.run(
            prefix
            + [
                "exec",
                "-T",
                "mysql",
                "sh",
                "-c",
                # The image's temporary initialization server accepts local sockets
                # before shutting down. TCP readiness excludes that transient server.
                'MYSQL_PWD="$MYSQL_PASSWORD" mysql --connect-timeout=5 -h127.0.0.1 -u"$MYSQL_USER" -N -e "SELECT 1" "$MYSQL_DATABASE"',
            ],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if test.returncode == 0:
            ready = True
            break
        time.sleep(2)
    if not ready:
        raise SystemExit("Database not ready; retain isolated resources for diagnosis")
    invoke(
        prefix
        + [
            "exec",
            "-T",
            "mysql",
            "sh",
            "-c",
            'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --connect-timeout=5 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE"',
        ],
        input=data,
        stdout=subprocess.DEVNULL,
    )
    invoke(
        prefix + ["up", "-d", "--wait", "--wait-timeout", "1800", "backend", "frontend"],
        stdout=subprocess.DEVNULL,
    )
    print(
        "Restored into isolated localhost instance. Verify orders, tickets and database hashes."
    )


if __name__ == "__main__":
    main()
