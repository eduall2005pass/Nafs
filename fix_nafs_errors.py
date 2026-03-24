#!/usr/bin/env python3
"""
Nafs Build Error Fix Script
Run from your repo root (~/storage/downloads/Nafs or wherever you cloned it)

Usage:
    python3 fix_nafs_errors.py

Then git add + push.
"""

import re
import os
import sys

# ─── File paths (relative to repo root) ───────────────────────────────────────
ACCESSIBILITY_FILE = "app/src/main/java/com/nafsshield/service/NafsAccessibilityService.kt"
PIN_FILE           = "app/src/main/java/com/nafsshield/ui/pin/PinActivity.kt"

def fix_accessibility_service(path):
    """
    Remove 'if (isInGracePeriod()) return' lines that appear OUTSIDE
    any function body (orphaned between closing '}' and next 'private fun').
    Pattern: line = '        if (isInGracePeriod()) return'
             previous non-blank line ends with '    }' (4-space function close)
    """
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    ORPHAN = "        if (isInGracePeriod()) return\n"
    ORPHAN2 = "        if (isInGracePeriod()) return\r\n"

    new_lines = []
    removed = 0

    for i, line in enumerate(lines):
        if line in (ORPHAN, ORPHAN2):
            # Look backward for the last non-blank line
            j = i - 1
            while j >= 0 and lines[j].strip() == "":
                j -= 1
            prev = lines[j].rstrip() if j >= 0 else ""

            # Look forward for next non-blank line
            k = i + 1
            while k < len(lines) and lines[k].strip() == "":
                k += 1
            nxt = lines[k].lstrip() if k < len(lines) else ""

            # If previous close-brace is at 4-space indent (end of function)
            # AND next line starts a new function/is a private fun → orphaned
            prev_is_func_end = (prev == "    }" or prev.endswith("    }"))
            next_is_func_start = (
                nxt.startswith("private fun") or
                nxt.startswith("fun ") or
                nxt.startswith("override fun") or
                nxt.startswith("internal fun") or
                nxt.startswith("// ──")
            )
            if prev_is_func_end and next_is_func_start:
                removed += 1
                print(f"  ✂  Removed orphaned guard at line {i+1}")
                continue  # skip this line
        new_lines.append(line)

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

    print(f"  ✅ NafsAccessibilityService.kt — removed {removed} orphaned line(s)")


def fix_pin_activity(path):
    """
    Fix literal newlines inside double-quoted Kotlin strings.
    Strings like:
        "⛔ ভুল PIN!
        
        অননুমোদিত..."
    become:
        "⛔ ভুল PIN!\\n\\nঅননুমোদিত..."
    """
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Track how many fixes were made
    fixes = 0

    # Strategy: find double-quoted strings that span multiple lines
    # and replace internal newlines with \n
    # We'll process the file character by character
    result = []
    i = 0
    in_string = False
    escape_next = False
    in_triple = False

    while i < len(content):
        c = content[i]

        if not in_string:
            # Check for triple-quoted string start
            if content[i:i+3] == '"""':
                in_triple = True
                in_string = True
                result.append('"""')
                i += 3
                continue
            # Check for normal double-quoted string start
            elif c == '"':
                in_string = True
                in_triple = False
                result.append('"')
                i += 1
                continue
            else:
                result.append(c)
                i += 1
                continue
        else:
            # Inside a string
            if escape_next:
                escape_next = False
                result.append(c)
                i += 1
                continue

            if in_triple:
                if content[i:i+3] == '"""':
                    result.append('"""')
                    i += 3
                    in_string = False
                    in_triple = False
                    continue
                elif c == '\n':
                    result.append(c)
                    i += 1
                    continue
                else:
                    result.append(c)
                    i += 1
                    continue
            else:
                # Normal double-quoted string
                if c == '\\':
                    escape_next = True
                    result.append(c)
                    i += 1
                    continue
                elif c == '"':
                    result.append(c)
                    i += 1
                    in_string = False
                    continue
                elif c == '\n':
                    # LITERAL newline inside a non-triple string — fix it!
                    fixes += 1
                    # Peek: if the next line is empty (just \n), emit \n\n
                    # Otherwise just emit \n
                    result.append('\\n')
                    i += 1
                    # Skip leading whitespace on continuation lines
                    while i < len(content) and content[i] in (' ', '\t'):
                        i += 1
                    continue
                else:
                    result.append(c)
                    i += 1
                    continue

    fixed_content = "".join(result)

    with open(path, "w", encoding="utf-8") as f:
        f.write(fixed_content)

    print(f"  ✅ PinActivity.kt — fixed {fixes} literal newline(s) inside strings")


def main():
    # Detect repo root
    if not os.path.exists(ACCESSIBILITY_FILE):
        print("❌ Cannot find project files.")
        print("   Make sure you run this script from the repo root directory.")
        print(f"   Expected: {os.getcwd()}/{ACCESSIBILITY_FILE}")
        sys.exit(1)

    print("\n🔧 Nafs Build Error Fixer\n")

    print("📄 Fixing NafsAccessibilityService.kt ...")
    fix_accessibility_service(ACCESSIBILITY_FILE)

    print("\n📄 Fixing PinActivity.kt ...")
    fix_pin_activity(PIN_FILE)

    print("\n✨ Done! Now run:")
    print("   git add app/src/main/java/com/nafsshield/service/NafsAccessibilityService.kt")
    print("   git add app/src/main/java/com/nafsshield/ui/pin/PinActivity.kt")
    print('   git commit -m "fix: resolve Kotlin compilation errors"')
    print("   git push\n")


if __name__ == "__main__":
    main()
