#!/usr/bin/env python3
import os
import re
import json

LOCALES = [
    "ar", "bg", "cs", "da", "de", "el", "es", "fi", "fr", "hi",
    "hu", "in", "it", "iw", "ja", "ko", "ms", "nb", "nl", "pl",
    "pt", "ro", "ru", "sk", "sv", "th", "tr", "uk", "vi", "zh-rCN"
]

master_path = "app/src/main/res/values/strings.xml"
with open(master_path, "r", encoding="utf-8") as f:
    master_content = f.read()

pattern = re.compile(r'<string\s+name="([^"]+)"(?:\s+formatted="([^"]+)")?\s*>(.*?)</string>', re.DOTALL)
master_items = []
for m in pattern.finditer(master_content):
    name = m.group(1)
    fmt = m.group(2)
    val = m.group(3)
    master_items.append((name, val, fmt == "false"))

print(f"Master keys count: {len(master_items)}")

# Load any already translated strings
existing = {}
for loc in LOCALES:
    existing[loc] = {}
    path = f"app/src/main/res/values-{loc}/strings.xml"
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            c = f.read()
        for m in pattern.finditer(c):
            existing[loc][m.group(1)] = m.group(3)

print("Existing translations loaded.")
