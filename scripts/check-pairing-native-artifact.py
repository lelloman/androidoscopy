#!/usr/bin/env python3
"""Verify SDK AAR or app APK embeds every supported, 16 KiB aligned JNI ABI."""

import struct
import sys
import zipfile

ABIS = ("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
LIBRARY = "libandroidoscopy_pairing_rate_limit.so"


def load_alignments(data: bytes) -> list[int]:
    assert data[:4] == b"\x7fELF", "JNI library is not ELF"
    assert data[5] == 1, "expected little-endian ELF"
    if data[4] == 1:
        phoff = struct.unpack_from("<I", data, 28)[0]
        phentsize, phnum = struct.unpack_from("<HH", data, 42)
        align_offset = 28
        align_format = "<I"
    else:
        assert data[4] == 2, "expected 32- or 64-bit ELF"
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from("<HH", data, 54)
        align_offset = 48
        align_format = "<Q"
    return [
        struct.unpack_from(align_format, data, phoff + i * phentsize + align_offset)[0]
        for i in range(phnum)
        if struct.unpack_from("<I", data, phoff + i * phentsize)[0] == 1
    ]


def main(artifact: str) -> None:
    prefix = "jni" if artifact.endswith(".aar") else "lib"
    with zipfile.ZipFile(artifact) as package:
        for abi in ABIS:
            member = f"{prefix}/{abi}/{LIBRARY}"
            entry = package.getinfo(member)
            alignments = load_alignments(package.read(entry))
            assert alignments and all(value >= 16_384 for value in alignments), (
                member,
                alignments,
            )
            if prefix == "lib" and entry.compress_type == zipfile.ZIP_STORED:
                with open(artifact, "rb") as stream:
                    stream.seek(entry.header_offset)
                    local_header = stream.read(30)
                assert local_header[:4] == b"PK\x03\x04"
                name_length, extra_length = struct.unpack_from("<HH", local_header, 26)
                data_offset = entry.header_offset + 30 + name_length + extra_length
                assert data_offset % 16_384 == 0, (member, data_offset)
                print(f"{member}: PT_LOAD {alignments}, APK data offset {data_offset} (16 KiB aligned)")
            else:
                print(f"{member}: PT_LOAD alignment {alignments}")


if __name__ == "__main__":
    main(sys.argv[1])
