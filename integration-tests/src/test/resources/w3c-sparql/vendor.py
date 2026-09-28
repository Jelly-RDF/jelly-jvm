#!/usr/bin/env python3
"""Copies the parts of the W3C SPARQL test suites that the Jelly-SPARQL tests use.

Usage: vendor.py <path to a checkout of https://github.com/w3c/rdf-tests>

Copies the SELECT/ASK result files (.srx, .srj, .tsv) that the manifests of sparql/sparql11 and
sparql/sparql12 refer to with mf:result, and those manifests. Everything else (queries, data,
CONSTRUCT results, CSV results) is left out. The directory layout is kept, so the relative links
in the manifests still work.
"""
import os
import re
import shutil
import subprocess
import sys

SUITES = ["sparql11", "sparql12"]
RESULT_EXTENSIONS = (".srx", ".srj", ".tsv")

source = os.path.join(sys.argv[1], "sparql")
target = os.path.dirname(os.path.abspath(__file__))

for suite in SUITES:
    shutil.rmtree(os.path.join(target, suite), ignore_errors=True)

copied = 0
for suite in SUITES:
    for directory, _, files in os.walk(os.path.join(source, suite)):
        if "manifest.ttl" not in files:
            continue
        manifest = os.path.join(directory, "manifest.ttl")
        results = []
        with open(manifest, encoding="utf-8") as f:
            for match in re.finditer(r"mf:result\s+<([^>]+)>", f.read()):
                if match.group(1).endswith(RESULT_EXTENSIONS):
                    results.append(os.path.normpath(os.path.join(directory, match.group(1))))
        # Manifests of syntax, update and protocol tests have nothing for us
        if not results:
            continue
        wanted = [manifest] + results
        for path in wanted:
            out = os.path.join(target, os.path.relpath(path, source))
            if not os.path.exists(out):
                os.makedirs(os.path.dirname(out), exist_ok=True)
                shutil.copyfile(path, out)
                copied += 1

shutil.copyfile(os.path.join(sys.argv[1], "LICENSE.md"), os.path.join(target, "LICENSE.md"))
commit = subprocess.check_output(["git", "-C", sys.argv[1], "rev-parse", "HEAD"], text=True).strip()
with open(os.path.join(target, "COMMIT"), "w", encoding="utf-8") as f:
    f.write(commit + "\n")
print(f"Copied {copied} files from w3c/rdf-tests at {commit}")
