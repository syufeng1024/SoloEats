#!/usr/bin/env bash
# 把 docs/tasks 下的单张任务卡派发给 Claude Code 执行（无头模式，可复用）
#
# 用法：
#   bash scripts/run-claude-task.sh docs/tasks/T01-system-rbac.md
#   bash scripts/run-claude-task.sh docs/tasks/T01-system-rbac.md "额外补充要求"
#
# 约定：
#   - 由 Claude Code 自动读取仓库根 CLAUDE.md 的项目规范
#   - 执行完成后自行编译验证；失败时在本脚本输出中体现
set -euo pipefail

TASK_FILE="${1:-}"
EXTRA="${2:-}"

if [[ -z "$TASK_FILE" ]]; then
  echo "用法: bash scripts/run-claude-task.sh <任务卡路径> [额外要求]" >&2
  exit 2
fi

if [[ ! -f "$TASK_FILE" ]]; then
  echo "任务卡不存在: $TASK_FILE" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PROMPT="$(cat <<EOF
你是本仓库的工程师。请严格按任务卡执行一个任务，不要扩大范围。

## 任务卡（先完整读取）
$TASK_FILE

## 执行纪律
1. 先读仓库根目录 CLAUDE.md，遵守其中的编码与构建约定。
2. 先读任务卡「规格书依据」里列出的规格书章节，再动手。
3. 只创建/修改任务卡「需要新增/修改的文件」中列出的文件；确有必要新增文件时，先说明理由。
4. 不得修改与本任务无关的模块，不得删除或弱化已有测试，不得改动既有对外接口契约。
5. 完成后必须执行验证：
   - mvn -q -pl <受影响模块> -am compile
   - mvn -q -DskipTests package
   任一失败就继续修复，直到通过。
6. 完成后在任务卡末尾追加一节 \`## 执行记录\`，写明：完成时间、实际改动文件清单、验证命令与结果、遗留问题。
7. 最后用结构化文本汇报：DONE/PARTIAL/BLOCKED、改动文件数、编译结果、遗留问题。

${EXTRA:+## 额外要求
$EXTRA
}
EOF
)"

echo "==> 任务卡: $TASK_FILE"
echo "==> 开始执行 Claude Code（无头模式）..."
echo

claude -p "$PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Read" "Write" "Edit" "Glob" "Grep" "Bash" \
  --output-format text \
  2>&1 | tail -n 120

echo
echo "==> 执行结束。请用 git status / git diff 复核改动。"
