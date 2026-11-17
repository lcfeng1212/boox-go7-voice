#!/usr/bin/env python3
"""Download official Vosk offline Chinese model, check ZIP safety, copy into Android assets."""
import hashlib
import json
import pathlib
import shutil
import tempfile
import urllib.request
import zipfile
NAME = "vosk-model-small-cn-0.22"
URL = "https://alphacephei.com/vosk/models/" + NAME + ".zip"
ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = ROOT / "app/src/main/assets/model-cn"
def main():
    if (DEST / "am/final.mdl").is_file() and (DEST / "conf/model.conf").is_file():
        print("Already have model")
        return
    with tempfile.TemporaryDirectory() as t:
        tmp = pathlib.Path(t)
        archive = tmp / "model.zip"
        with urllib.request.urlopen(URL, timeout=180) as response, archive.open("wb") as out:
            shutil.copyfileobj(response, out)
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        with zipfile.ZipFile(archive) as z:
            total = 0
            for entry in z.infolist():
                path = pathlib.PurePosixPath(entry.filename)
                mode = (entry.external_attr >> 16) & 0o170000
                if not path.parts or path.parts[0] != NAME or ".." in path.parts or mode == 0o120000:
                    raise ValueError("Unsafe ZIP member: " + entry.filename)
                total += entry.file_size
                if total > 350 * 1024 * 1024: raise ValueError("Model too large")
            z.extractall(tmp / "unzipped")
        model = tmp / "unzipped" / NAME
        if not (model / "am/final.mdl").is_file() or not (model / "conf/model.conf").is_file():
            raise ValueError("Required model files missing")
        if DEST.exists(): shutil.rmtree(DEST)
        DEST.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(model, DEST)
        (ROOT / "model-source.json").write_text(
            json.dumps({"name": NAME, "source": URL, "sha256": digest, "license": "Apache-2.0"}, indent=2) + "\n"
        )
        print("Model SHA256:", digest)
if __name__ == "__main__": main()
