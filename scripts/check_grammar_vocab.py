#!/usr/bin/env python3
"""Fail if any Vosk grammar token is missing from the model's word symbol table.

Vosk silently drops grammar words that are not in the model vocabulary, which would make a
voice command impossible to recognise. The small Chinese model has no words.txt; its word
symbols are embedded in graph/Gr.fst as OpenFST SymbolTable entries (int32 length + UTF-8).
Usage: java ... GrammarDump | python3 scripts/check_grammar_vocab.py app/src/main/assets/model-cn
"""
import os
import pathlib
import struct
import sys

def main():
    graph = pathlib.Path(sys.argv[1]) / "graph" / "Gr.fst"
    data = graph.read_bytes()
    tokens = sorted({t for line in sys.stdin for t in line.split()})
    if not tokens:
        sys.exit("no grammar tokens received")
    def known(word):
        return data.find(struct.pack("<i", len(word.encode())) + word.encode()) >= 0
    missing = [t for t in tokens if not known(t)]
    # Optional non-fatal probe, e.g. PROBE_WORDS="左滑 左 滑", to find how the model splits a phrase.
    for w in os.environ.get("PROBE_WORDS", "").split():
        print("probe   %-8s %s" % (w, "in vocabulary" if known(w) else "missing"))
    for t in tokens:
        print(("MISSING " if t in missing else "ok      ") + t)
    if missing:
        sys.exit("grammar tokens missing from model vocabulary: " + " ".join(missing))
    print("PASS %d grammar tokens found in %s" % (len(tokens), graph))

if __name__ == "__main__":
    main()
