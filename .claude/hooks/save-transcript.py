#!/usr/bin/env python3
"""
Stop hook: 매 turn 종료 시 호출되어 현재 세션의 transcript에서
user 입력 / 옵션 선택 / assistant 응답 텍스트만 추출해 마크다운으로 저장한다.

저장 위치: $CLAUDE_PROJECT_DIR/.claude/conversation-history/<session-id>.md
첨부 이미지: $CLAUDE_PROJECT_DIR/.claude/conversation-history/<session-id>/image-*.{ext}

/clear 시 SessionEnd hook이 발화하지 않는 이슈를 우회하기 위해
매 turn마다 전체 transcript를 다시 변환해 덮어쓴다.
원본 누적 JSONL은 ~/.claude/projects/<encoded-cwd>/<session-id>.jsonl 에 있다.

추출 대상:
  - 직접 입력한 user 메시지 (content가 string 또는 list)
  - 첨부 이미지 (list content의 image 블록을 base64 디코딩해 파일로 저장)
  - AskUserQuestion 도구 응답 (옵션 선택 형태의 의사결정)
  - assistant의 text 응답 (thinking, tool_use는 제외)
"""

import base64
import json
import os
import sys
from pathlib import Path


ASK_PREFIX = "User has answered your questions: "
ASK_SUFFIX = ". You can now continue with the user's answers in mind."


def extract_assistant_text(content):
    """assistant content array에서 text 타입만 합쳐 반환."""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        texts = [
            c.get("text", "")
            for c in content
            if isinstance(c, dict) and c.get("type") == "text"
        ]
        return "\n".join(t for t in texts if t).strip()
    return ""


def extract_tool_result_text(content):
    """tool_result.content는 string이거나 [{type:text, text:...}] 형태."""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        texts = [
            c.get("text", "")
            for c in content
            if isinstance(c, dict) and c.get("type") == "text"
        ]
        return "\n".join(t for t in texts if t).strip()
    return ""


def clean_ask_user_text(text):
    """AskUserQuestion 결과의 prefix/suffix를 제거해 가독성을 높인다."""
    if text.startswith(ASK_PREFIX):
        text = text[len(ASK_PREFIX):]
    if text.endswith(ASK_SUFFIX):
        text = text[: -len(ASK_SUFFIX)]
    return text.strip()


def collect_ask_user_ids(entries):
    """assistant tool_use 중 AskUserQuestion의 id 집합을 수집한다."""
    ids = set()
    for entry in entries:
        if entry.get("type") != "assistant":
            continue
        content = (entry.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        for c in content:
            if (
                isinstance(c, dict)
                and c.get("type") == "tool_use"
                and c.get("name") == "AskUserQuestion"
            ):
                ids.add(c.get("id"))
    return ids


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        sys.exit(0)

    session_id = payload.get("session_id", "")
    cwd = payload.get("cwd", os.getcwd())
    transcript_path = payload.get("transcript_path", "")

    if not transcript_path and session_id and cwd:
        encoded = cwd.replace("/", "-")
        transcript_path = os.path.expanduser(
            f"~/.claude/projects/{encoded}/{session_id}.jsonl"
        )

    project_root = os.environ.get("CLAUDE_PROJECT_DIR", cwd)
    dest_dir = Path(project_root) / ".claude" / "conversation-history"
    dest_dir.mkdir(parents=True, exist_ok=True)

    if not (transcript_path and Path(transcript_path).is_file() and session_id):
        sys.exit(0)

    image_dir = dest_dir / session_id

    entries = []
    with open(transcript_path, "r", encoding="utf-8") as f:
        for raw in f:
            try:
                entries.append(json.loads(raw))
            except json.JSONDecodeError:
                continue

    ask_user_ids = collect_ask_user_ids(entries)

    def save_image_block(block, line_idx, block_idx):
        """user content의 image 블록을 디코딩해 파일로 저장하고 마크다운 링크를 반환."""
        src = block.get("source") or {}
        if src.get("type") != "base64":
            return None
        media = src.get("media_type", "image/png")
        ext = media.split("/")[-1] or "png"
        try:
            data = base64.b64decode(src.get("data", ""))
        except (ValueError, TypeError):
            return None
        if not data:
            return None
        image_dir.mkdir(parents=True, exist_ok=True)
        fname = f"image-line{line_idx}-block{block_idx}.{ext}"
        path = image_dir / fname
        if not path.exists():
            path.write_bytes(data)
        return f"![{fname}]({session_id}/{fname})"

    # turn 단위로 묶는다. user 입력 / User (Choice) 가 새 turn 경계.
    # 한 turn 내 발생한 모든 assistant text 응답은 하나의 블록으로 합친다.
    turns = []
    current = None

    def start_turn(ts, role, text):
        nonlocal current
        if current is not None:
            turns.append(current)
        current = {
            "user_ts": ts,
            "user_role": role,
            "user_text": text,
            "assistant_ts": None,
            "assistant_texts": [],
        }

    for line_idx, entry in enumerate(entries):
        entry_type = entry.get("type")
        if entry_type not in ("user", "assistant"):
            continue

        msg = entry.get("message") or {}
        content = msg.get("content")
        ts = entry.get("timestamp", "")

        if entry_type == "assistant":
            text = extract_assistant_text(content)
            if not text:
                continue
            if current is None:
                # 첫 user 입력 이전의 assistant 응답 (드문 케이스)
                current = {
                    "user_ts": "",
                    "user_role": None,
                    "user_text": None,
                    "assistant_ts": None,
                    "assistant_texts": [],
                }
            if current["assistant_ts"] is None:
                current["assistant_ts"] = ts
            current["assistant_texts"].append(text)
            continue

        # user
        if isinstance(content, str):
            text = content.strip()
            if text:
                start_turn(ts, "User", text)
            continue

        if not isinstance(content, list):
            continue

        # list content: 직접 입력(text+image 첨부)과 AskUserQuestion 응답을 구분.
        text_parts = []
        image_links = []
        ask_texts = []
        for block_idx, c in enumerate(content):
            if not isinstance(c, dict):
                continue
            ctype = c.get("type")
            if ctype == "text":
                t = (c.get("text") or "").strip()
                # 클립보드 이미지 첨부 시 자동 주입되는 메타 텍스트는 노이즈라 무시.
                if t and not t.startswith("[Image: source:"):
                    text_parts.append(t)
            elif ctype == "image":
                link = save_image_block(c, line_idx, block_idx)
                if link:
                    image_links.append(link)
            elif ctype == "tool_result" and c.get("tool_use_id") in ask_user_ids:
                t = extract_tool_result_text(c.get("content"))
                t = clean_ask_user_text(t)
                if t:
                    ask_texts.append(t)

        # 직접 입력 (text 또는 image 첨부) 가 있으면 새 user turn.
        if text_parts or image_links:
            sections = []
            if text_parts:
                sections.append("\n".join(text_parts))
            if image_links:
                sections.append("\n".join(image_links))
            start_turn(ts, "User", "\n\n".join(sections))

        # AskUserQuestion 응답은 별도 turn 으로 (직접 입력과 같은 메시지에 섞이지 않음).
        for t in ask_texts:
            start_turn(ts, "User (Choice)", t)

    if current is not None:
        turns.append(current)

    out_lines = [f"# Conversation: {session_id}", ""]
    for turn in turns:
        if turn["user_role"] and turn["user_text"]:
            header = (
                f"## [{turn['user_ts']}] {turn['user_role']}"
                if turn["user_ts"]
                else f"## {turn['user_role']}"
            )
            out_lines.extend([header, "", turn["user_text"], "", "---", ""])
        if turn["assistant_texts"]:
            ats = turn["assistant_ts"]
            header = f"## [{ats}] Assistant" if ats else "## Assistant"
            merged = "\n\n".join(turn["assistant_texts"])
            out_lines.extend([header, "", merged, "", "---", ""])

    dest = dest_dir / f"{session_id}.md"
    dest.write_text("\n".join(out_lines), encoding="utf-8")


if __name__ == "__main__":
    main()
