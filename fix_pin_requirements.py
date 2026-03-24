#!/usr/bin/env python3
"""
PIN requirements fix:
- App open: NO pin
- Blocked apps add: NO pin (already no pin)
- Keyword add: NO pin
- Website add: NO pin
- Report view: NO pin

PIN still required for:
- Remove/unblock/delete → PIN ✅ (unchanged)
- Keyword toggle OFF → PIN ✅ (unchanged)
"""

import re

# ── 1. MainActivity - app open এ PIN সরাও ─────────────────────────────────
path = "app/src/main/java/com/nafsshield/ui/MainActivity.kt"
with open(path) as f:
    content = f.read()

# onCreate এ PIN verify check সরাও
old = """            when {
                !pinManager.isPinSetup  -> launchPin(PinActivity.MODE_SETUP)
                !PinActivity.isVerified -> launchPin(PinActivity.MODE_VERIFY)
                else -> checkOverlayAndStart()
            }"""
new = """            when {
                !pinManager.isPinSetup -> launchPin(PinActivity.MODE_SETUP)
                else -> checkOverlayAndStart()
            }"""
if old in content:
    content = content.replace(old, new)
    print("MainActivity: removed PIN verify on open (onCreate)")
else:
    print("MainActivity onCreate: pattern not found!")

# onStart এ background থেকে ফিরলে PIN check সরাও
old2 = """        if (wentToBackground && !PinActivity.isVerified) {
            launchPin(PinActivity.MODE_VERIFY)
            wentToBackground = false
        }"""
new2 = """        // PIN not required on re-open
        wentToBackground = false"""
if old2 in content:
    content = content.replace(old2, new2)
    print("MainActivity: removed PIN verify on background return (onStart)")
else:
    print("MainActivity onStart: pattern not found!")

with open(path, "w") as f:
    f.write(content)

# ── 2. KeywordsFragment - add থেকে PIN সরাও ──────────────────────────────
path = "app/src/main/java/com/nafsshield/ui/keywords/KeywordsFragment.kt"
with open(path) as f:
    content = f.read()

old = """    private fun verifyPinThenAdd() {
        val word = etKeyword.text?.toString()?.trim() ?: ""
        if (word.isEmpty()) {
            Snackbar.make(requireView(), getString(R.string.keyword_empty_error), Snackbar.LENGTH_SHORT).show()
            return
        }
        val dv = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etPinVerify)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage("\\"$word\\" keyword যোগ করতে PIN দিন")
            .setView(dv).setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null).create()
        dlg.show(); et.requestFocus()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (val r = pinManager.verifyPin(et.text?.toString() ?: "")) {
                is PinResult.Correct -> {
                    viewModel.addKeyword(word); etKeyword.text?.clear()
                    dlg.dismiss()
                    Snackbar.make(requireView(), "\\"$word\\" যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
                }
                is PinResult.Wrong -> { et.text?.clear(); et.error = "❌ ভুল PIN! বাকি: ${r.attemptsLeft}" }
                is PinResult.LockedOut -> {
                    dlg.dismiss()
                    Snackbar.make(requireView(), "🔒 ${r.secondsRemaining/60} মিনিট লক", Snackbar.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }"""

new = """    private fun verifyPinThenAdd() {
        val word = etKeyword.text?.toString()?.trim() ?: ""
        if (word.isEmpty()) {
            Snackbar.make(requireView(), getString(R.string.keyword_empty_error), Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.addKeyword(word)
        etKeyword.text?.clear()
        Snackbar.make(requireView(), "\\"$word\\" যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
    }"""

if old in content:
    content = content.replace(old, new)
    print("KeywordsFragment: PIN removed from add")
else:
    print("KeywordsFragment: pattern not found!")

with open(path, "w") as f:
    f.write(content)

# ── 3. WebsitesFragment - add থেকে PIN সরাও ─────────────────────────────
path = "app/src/main/java/com/nafsshield/ui/websites/WebsitesFragment.kt"
with open(path) as f:
    content = f.read()

old = """    private fun addWebsite() {
        val raw = etWebsite.text?.toString()?.trim() ?: ""
        if (raw.isBlank()) {
            Snackbar.make(requireView(), "URL বা domain দিন", Snackbar.LENGTH_SHORT).show()
            return
        }
        val dv = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etPinVerify)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage(raw + " block করতে PIN দিন")
            .setView(dv).setPositiveButton("Block করুন", null)
            .setNegativeButton("Cancel", null).create()
        dlg.show(); et.requestFocus()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (val r = pinManager.verifyPin(et.text?.toString() ?: "")) {
                is PinResult.Correct -> {
                    pinManager.addBlockedWebsite(raw)
                    etWebsite.text?.clear()
                    dlg.dismiss(); refreshList()
                    Snackbar.make(requireView(), raw + " block হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
                }
                is PinResult.Wrong -> { et.text?.clear(); et.error = "❌ ভুল PIN! বাকি: " + r.attemptsLeft }
                is PinResult.LockedOut -> {
                    dlg.dismiss()
                    Snackbar.make(requireView(), "🔒 " + (r.secondsRemaining/60) + " মিনিট লক", Snackbar.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }"""

new = """    private fun addWebsite() {
        val raw = etWebsite.text?.toString()?.trim() ?: ""
        if (raw.isBlank()) {
            Snackbar.make(requireView(), "URL বা domain দিন", Snackbar.LENGTH_SHORT).show()
            return
        }
        pinManager.addBlockedWebsite(raw)
        etWebsite.text?.clear()
        refreshList()
        Snackbar.make(requireView(), raw + " block হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
    }"""

if old in content:
    content = content.replace(old, new)
    print("WebsitesFragment: PIN removed from add")
else:
    print("WebsitesFragment: pattern not found!")

with open(path, "w") as f:
    f.write(content)

print("\n✅ All done!")
print("\nNow run:")
print("git add app/src/main/java/com/nafsshield/ui/MainActivity.kt \\")
print("        app/src/main/java/com/nafsshield/ui/keywords/KeywordsFragment.kt \\")
print("        app/src/main/java/com/nafsshield/ui/websites/WebsitesFragment.kt && \\")
print('git commit -m "feat: remove PIN from open/add/report, keep for remove/settings" && \\')
print("git push")
