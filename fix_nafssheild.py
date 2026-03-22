#!/usr/bin/env python3
"""
NafsShield - SettingsFragment.kt fix
Termux এ: python fix_nafssheild.py
"""
import os, shutil

BASE_CANDIDATES = [".", "NafsShield"]

def find_base():
    for base in BASE_CANDIDATES:
        if os.path.isfile(os.path.join(base, "app/src/main/AndroidManifest.xml")):
            return base
    custom = input("Project root: ").strip()
    if os.path.isfile(os.path.join(custom, "app/src/main/AndroidManifest.xml")):
        return custom
    print("❌ Not found"); exit(1)

base = find_base()
SETTINGS = os.path.join(base, "app/src/main/java/com/nafsshield/ui/settings/SettingsFragment.kt")

if not os.path.isfile(SETTINGS):
    print(f"❌ Missing: {SETTINGS}"); exit(1)

shutil.copy2(SETTINGS, SETTINGS + ".bak")

with open(SETTINGS, "r", encoding="utf-8") as f:
    content = f.read()

# পুরনো rowChangePin block (buggy বা original যাই থাকুক)
# লাইন-by-লাইন parse করে সঠিকভাবে replace করো
lines = content.split('\n')
new_lines = []
i = 0
replaced = False

while i < len(lines):
    line = lines[i]

    if 'rowChangePin' in line and 'setOnClickListener' in line and not replaced:
        # এই পুরো block টা skip করে নতুন correct block লিখি
        indent = len(line) - len(line.lstrip())
        sp = ' ' * indent
        sp4 = ' ' * (indent + 4)
        sp8 = ' ' * (indent + 8)
        sp12 = ' ' * (indent + 12)

        # Correct replacement
        new_lines.append(sp + 'view.findViewById<View>(R.id.rowChangePin).setOnClickListener {')
        new_lines.append(sp4 + '(requireActivity() as MainActivity).pinChangeLauncher.launch(')
        new_lines.append(sp8 + 'Intent(requireContext(), PinActivity::class.java).apply {')
        new_lines.append(sp12 + 'putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)')
        new_lines.append(sp8 + '}')
        new_lines.append(sp4 + ')')
        new_lines.append(sp + '}')

        replaced = True
        i += 1

        # পুরনো block এর closing brace পর্যন্ত skip করো
        depth = 1  # setOnClickListener { এর জন্য
        while i < len(lines) and depth > 0:
            l = lines[i]
            depth += l.count('{') - l.count('}')
            i += 1
        continue

    new_lines.append(line)
    i += 1

if replaced:
    with open(SETTINGS, "w", encoding="utf-8") as f:
        f.write('\n'.join(new_lines))
    print("✅ SettingsFragment.kt fixed!")
else:
    print("⚠️  rowChangePin not found — may already be fixed")

print()
print("▶ এখন:")
print("  git add -A")
print('  git commit -m "fix: SettingsFragment syntax error"')
print("  git push")
