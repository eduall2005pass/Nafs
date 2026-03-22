#!/usr/bin/env python3
import os

BASHRC = os.path.expanduser("~/.bashrc")

PASTE_CODE = '''
# ── Ridmok V long-press → Paste (Ctrl+V) ──
paste_clipboard() {
    CLIP=$(termux-clipboard-get 2>/dev/null)
    READLINE_LINE="${READLINE_LINE:0:$READLINE_POINT}${CLIP}${READLINE_LINE:$READLINE_POINT}"
    READLINE_POINT=$((READLINE_POINT + ${#CLIP}))
}
bind -x '"\\C-v": paste_clipboard'
# ──────────────────────────────────────────
'''

def setup():
    if os.path.exists(BASHRC):
        with open(BASHRC, "r") as f:
            if "paste_clipboard" in f.read():
                print("✅ Already configured!")
                print("   source ~/.bashrc")
                return
    with open(BASHRC, "a") as f:
        f.write(PASTE_CODE)
    print("✅ Setup সম্পন্ন!")
    print("এখন run করো: source ~/.bashrc")

if __name__ == "__main__":
    setup()
