#!/usr/bin/env python3
"""Fail if any Vosk grammar token is missing from the model's word symbol table.

Vosk silently drops grammar words that are not in the model vocabulary, which would make a
voice command impossible to recognise. The small Chinese model has no words.txt; its word
symbols are embedded in graph/Gr.fst as OpenFST SymbolTable entries (int32 length + UTF-8).
Usage: java ... GrammarDump | python3 scripts/check_grammar_vocab.py app/src/main/assets/model-cn
"""
import pathlib
import struct
import sys

def main():
    graph = pathlib.Path(sys.argv[1]) / "graph" / "Gr.fst"
    data = graph.read_bytes()
    tokens = sorted({t for line in sys.stdin for t in line.split()})
    if not tokens:
        sys.exit("no grammar tokens received")
    missing = [t for t in tokens if data.find(struct.pack("<i", len(t.encode())) + t.encode()) < 0]
    for t in tokens:
        print(("MISSING " if t in missing else "ok      ") + t)
    if missing:
        sys.exit("grammar tokens missing from model vocabulary: " + " ".join(missing))
    print("PASS %d grammar tokens found in %s" % (len(tokens), graph))

if __name__ == "__main__":
    main()
