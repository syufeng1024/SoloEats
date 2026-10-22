#!/usr/bin/env python3
"""SoloEats 项目进程 Dashboard 回写工具。

把 Claude Code 的会话回执（RECEIPT JSON）写回 docs/tasks/dashboard.html 的
DASHBOARD_DATA 数据块，实现「派发 → 执行 → 回执 → 回写」闭环。

只依赖标准库，Python 3.9+。

用法
----
    python scripts/dashboard_update.py --receipt .workbuddy/receipts/T03-A.json
    python scripts/dashboard_update.py --receipt r.json --dry-run
    python scripts/dashboard_update.py --baseline auto
    python scripts/dashboard_update.py --check

回执字段（缺省即跳过，不报错）
------------------------------
    card        任务卡号，如 "T03"（必需）
    stage       阶段标识，如 "A" / "B" / "C" / "A-0" / "0"（缺省 "A"）
    status      done | doing | todo | fail        （缺省 done）
    commit      短 hash；不存在于 git log 时仅告警
    commitMsg   提交信息
    maven       {"result":"pass|fail|skip","detail":"..."}
    tests       "12 passed"
    files       {"added":18,"modified":6,"deleted":0}
    migrations  ["V7__xxx.sql"]
    issues      ["..."]        覆盖式写入该卡遗留 issue
    front       {"end":"平台运营后台","delta":2}  前端页交付增量
    log         {"type":"feat|fix|docs|audit|doing","title":"...","detail":"..."}
    baseline    "main@9f2c1ab"，覆盖页头基线
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone

def _find_repo_root(start):
    """向上查找含 .git 的目录作为仓库根；找不到则退回上三级（本脚本位于 .workbuddy/dashboard/）。"""
    cur = os.path.abspath(start)
    for _ in range(8):
        if os.path.exists(os.path.join(cur, ".git")):
            return cur
        parent = os.path.dirname(cur)
        if parent == cur:
            break
        cur = parent
    return os.path.dirname(os.path.dirname(os.path.abspath(start)))


# 脚本位置：<repo>/.workbuddy/dashboard/dashboard_update.py
ROOT = _find_repo_root(__file__)
DEFAULT_DASHBOARD = os.path.join(ROOT, ".workbuddy", "dashboard", "dashboard.html")
CST = timezone(timedelta(hours=8))

BEGIN = "<!-- DATA:BEGIN -->"
END = "<!-- DATA:END -->"
SCRIPT_RE = re.compile(
    r'(<script id="dashboard-data" type="application/json">)(.*?)(</script>)', re.S
)

STATUS_ALIAS = {
    "done": "done", "completed": "done", "pass": "done", "success": "done", "已完成": "done",
    "doing": "doing", "in_progress": "doing", "wip": "doing", "fail": "doing", "failed": "doing", "进行中": "doing",
    "todo": "todo", "pending": "todo", "未开始": "todo",
}


# ---------------------------------------------------------------- 读写数据块
def load_html(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def extract_data(html: str) -> tuple[str, str, str]:
    """返回 (前缀, JSON 文本, 后缀)，定位失败抛 SystemExit。"""
    m = SCRIPT_RE.search(html)
    if not m:
        sys.exit(f"[FAIL] 未找到 <script id=\"dashboard-data\"> 数据块：{DEFAULT_DASHBOARD}")
    if BEGIN not in html or END not in html:
        sys.exit("[FAIL] 未找到 DATA:BEGIN / DATA:END 标记")
    return m.group(1), m.group(2), m.group(3)


def dump_data(data: dict) -> str:
    return "\n" + json.dumps(data, ensure_ascii=False, indent=2) + "\n"


# ---------------------------------------------------------------- 业务回写
def now_str() -> str:
    return datetime.now(CST).strftime("%Y-%m-%d %H:%M")


def git_has_commit(hash_: str) -> bool:
    if not hash_ or hash_ in {"—", "-", "uncommitted", "未提交"}:
        return True
    try:
        r = subprocess.run(
            ["git", "-C", ROOT, "cat-file", "-e", f"{hash_}^{{commit}}"],
            capture_output=True,
        )
        return r.returncode == 0
    except OSError:
        return True  # 无 git 环境时不阻断


def git_baseline() -> str | None:
    try:
        br = subprocess.run(
            ["git", "-C", ROOT, "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True,
        ).stdout.strip()
        sh = subprocess.run(
            ["git", "-C", ROOT, "rev-parse", "--short", "HEAD"],
            capture_output=True, text=True,
        ).stdout.strip()
        return f"{br}@{sh}" if br and sh else None
    except OSError:
        return None


def recompute_card_status(card: dict) -> str:
    states = [s.get("s", "todo") for s in card.get("stages", [])]
    if states and all(s == "done" for s in states):
        return "done"
    if any(s in {"done", "doing"} for s in states):
        return "doing"
    return "todo"


def apply_receipt(data: dict, receipt: dict, args) -> list[str]:
    changes: list[str] = []
    card_id = receipt.get("card")
    if not card_id:
        sys.exit("[FAIL] 回执缺少 card 字段")

    stage_key = str(receipt.get("stage", "A"))
    raw_status = str(receipt.get("status", "done")).lower()
    status = STATUS_ALIAS.get(raw_status, "doing")
    failed = raw_status in {"fail", "failed"}

    cards = data.setdefault("cards", [])
    card = next((c for c in cards if c.get("id") == card_id), None)
    if card is None:
        card = {
            "id": card_id,
            "title": receipt.get("title") or card_id,
            "module": receipt.get("module", "—"),
            "pri": receipt.get("pri", "P1"),
            "status": "todo",
            "stages": [],
            "maven": {"r": "skip", "d": "未执行"},
            "issues": [],
        }
        cards.append(card)
        changes.append(f"新建任务卡 {card_id}")

    # 阶段
    stages = card.setdefault("stages", [])
    stage = next((s for s in stages if str(s.get("k")) == stage_key), None)
    if stage is None:
        stage = {"k": stage_key, "s": "todo"}
        stages.append(stage)
        changes.append(f"{card_id} 追加阶段 {stage_key}")
    commit = receipt.get("commit")
    stage["s"] = status
    if commit:
        stage["c"] = commit
    elif status == "doing":
        stage.pop("c", None)
    changes.append(f"{card_id}-{stage_key} → {status}" + (f" @ {commit}" if commit else ""))

    # 卡片聚合状态
    card["status"] = recompute_card_status(card)

    # Maven
    maven = receipt.get("maven")
    if isinstance(maven, dict) and maven.get("result"):
        res = str(maven["result"]).lower()
        res = {"success": "pass", "ok": "pass", "passed": "pass", "failed": "fail"}.get(res, res)
        card["maven"] = {"r": res, "d": str(maven.get("detail", "")) or res}
        changes.append(f"{card_id} maven={res}")

    if receipt.get("tests"):
        card["tests"] = str(receipt["tests"])

    # 遗留 issue（覆盖式）
    issues = list(receipt.get("issues") or [])
    if failed:
        detail = (receipt.get("maven") or {}).get("detail") or "构建/自检失败"
        issues.insert(0, f"❌ 自检失败：{detail}")
    if issues or "issues" in receipt:
        card["issues"] = issues
        changes.append(f"{card_id} issues={len(issues)}")

    # 前端页交付（可选）
    front = receipt.get("front")
    if isinstance(front, dict) and front.get("end"):
        for e in data.get("srs", {}).get("ends", []):
            if e.get("name") == front["end"]:
                e["done"] = max(0, int(e.get("done", 0)) + int(front.get("delta", 0)))
                changes.append(f"前端 {front['end']} → {e['done']}/{e['total']}")
        for block in ("admin", "merchant"):
            sec = data.get("srs", {}).get(block) or {}
            if sec.get("title") == front["end"]:
                sec["delivered"] = max(0, int(sec.get("delivered", 0)) + int(front.get("delta", 0)))

    # 日志流
    log = receipt.get("log") or {}
    # 标题必须与真实状态一致：只有 done 才允许出现「完成」，避免把进行中写成完成
    if status == "done":
        default_title = f"{card_id} 阶段{stage_key} 完成"
    elif failed:
        default_title = f"{card_id} 阶段{stage_key} 编译失败/中断"
    elif status == "doing":
        default_title = f"{card_id} 阶段{stage_key} 进行中（未提交/未验证）"
    else:
        default_title = f"{card_id} 阶段{stage_key} 状态回撤为未开始"
    title = log.get("title") or default_title
    detail_bits = [b for b in [
        receipt.get("commitMsg"),
        (receipt.get("maven") or {}).get("detail"),
        "迁移 " + "、".join(receipt["migrations"]) if receipt.get("migrations") else None,
    ] if b]
    files = receipt.get("files") or {}
    stat = (
        f"{files.get('added', 0)} 新增 / {files.get('modified', 0)} 修改"
        if files else ("未提交（工作区）" if not commit else "—")
    )
    entry = {
        "at": now_str(),
        "type": log.get("type") or ("fix" if failed else ("doing" if status != "done" else "feat")),
        "ref": commit or "—",
        "title": title,
        "detail": log.get("detail") or "；".join(detail_bits) or f"{card_id}-{stage_key}",
        "stat": stat,
    }
    if status != "done" and "完成" in entry["title"]:
        print(f"  [WARN] 回执标题含「完成」但状态为 {status}，已按状态改题为：{default_title}")
        entry["title"] = default_title
    data.setdefault("logs", []).insert(0, entry)
    changes.append("logs 头部插入 1 条")

    # 元信息
    data.setdefault("meta", {})["updatedAt"] = now_str()
    if receipt.get("baseline"):
        data["meta"]["baseline"] = receipt["baseline"]
    elif args.baseline == "auto":
        b = git_baseline()
        if b:
            data["meta"]["baseline"] = b
    if args.batch:
        data["meta"]["currentBatch"] = args.batch

    return changes


# ---------------------------------------------------------------- 校验
def self_check(data: dict) -> list[str]:
    errs = []
    cards = data.get("cards", [])
    if len(cards) != data.get("meta", {}).get("totalCards", len(cards)):
        errs.append(
            f"任务卡数 {len(cards)} 与 meta.totalCards {data['meta'].get('totalCards')} 不一致"
        )
    ids = [c.get("id") for c in cards]
    if len(ids) != len(set(ids)):
        errs.append("cards 存在重复卡号")
    for c in cards:
        for s in c.get("stages", []):
            if s.get("s") not in {"done", "doing", "todo"}:
                errs.append(f"{c.get('id')} 阶段 {s.get('k')} 状态非法：{s.get('s')}")
        expect = recompute_card_status(c)
        if c.get("status") != expect:
            errs.append(f"{c.get('id')} 卡级状态 {c.get('status')} 与阶段不一致（应为 {expect}）")
    admin = data.get("srs", {}).get("admin", {})
    if admin:
        s = sum(r.get("pages", 0) for r in admin.get("rows", []))
        if admin.get("pages") and s != admin["pages"]:
            errs.append(f"运营后台模块页数合计 {s} ≠ {admin['pages']}")
    mer = data.get("srs", {}).get("merchant", {})
    if mer:
        s = sum(r.get("pages", 0) for r in mer.get("rows", []))
        if mer.get("pages") and s != mer["pages"]:
            errs.append(f"商家中心模块页数合计 {s} ≠ {mer['pages']}")
    return errs


def print_stats(data: dict) -> None:
    cards = data["cards"]
    cnt = {k: sum(1 for c in cards if c["status"] == k) for k in ("done", "doing", "todo")}
    units = sum(len(c["stages"]) for c in cards)
    done_units = sum(1 for c in cards for s in c["stages"] if s["s"] == "done")
    print(f"  任务卡  : 已完成 {cnt['done']} / 进行中 {cnt['doing']} / 未开始 {cnt['todo']}（共 {len(cards)}）")
    print(f"  完成率  : {round(cnt['done'] / len(cards) * 100, 1)}%")
    print(f"  会话单元: {done_units} / {units} 已完成")
    print(f"  快照    : {data['meta']['baseline']} · {data['meta']['updatedAt']}")


# ---------------------------------------------------------------- 主流程
def main() -> int:
    ap = argparse.ArgumentParser(description="SoloEats Dashboard 回写工具")
    ap.add_argument("--receipt", help="RECEIPT JSON 路径")
    ap.add_argument("--dashboard", default=DEFAULT_DASHBOARD)
    ap.add_argument("--baseline", default="", help="页头基线，传 auto 则取 git 当前 HEAD")
    ap.add_argument("--batch", default="", help="覆盖 meta.currentBatch")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true", help="仅校验，不写入")
    args = ap.parse_args()

    html = load_html(args.dashboard)
    pre, body, post = extract_data(html)
    try:
        data = json.loads(body)
    except json.JSONDecodeError as e:
        sys.exit(f"[FAIL] 数据块 JSON 非法：{e}")

    if not args.receipt:
        errs = self_check(data)
        print("=== Dashboard 校验 ===")
        print_stats(data)
        if errs:
            print("\n[警告]")
            for e in errs:
                print("  -", e)
            return 1
        print("\n[DONE] 校验通过")
        return 0

    with open(args.receipt, encoding="utf-8") as f:
        receipt = json.load(f)

    commit = receipt.get("commit")
    if commit and not git_has_commit(commit):
        print(f"[WARN] commit {commit} 不在 git 历史中，请确认是否真的已提交")

    changes = apply_receipt(data, receipt, args)

    errs = self_check(data)
    print("=== 回执应用 ===")
    for c in changes:
        print("  ·", c)
    if errs:
        print("\n[警告] 数据一致性：")
        for e in errs:
            print("  -", e)

    if args.dry_run:
        print("\n[dry-run] 未写入文件")
        print_stats(data)
        return 0

    m = SCRIPT_RE.search(html)
    new_html = html[: m.start(2)] + dump_data(data) + html[m.end(2):]

    tmp = args.dashboard + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_html)
    os.replace(tmp, args.dashboard)

    print(f"\n[DONE] 已回写 {os.path.relpath(args.dashboard, ROOT)}")
    print_stats(data)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
