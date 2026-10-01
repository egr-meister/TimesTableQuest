#!/usr/bin/env python3
"""Checks 16 KB page-size compatibility of native libraries inside APK/AAB files.

For every lib/**/*.so (APK) or base/lib/**/*.so (AAB):
  * ELF PT_LOAD segments must have p_align >= 16384 (2**14).
For APKs additionally:
  * uncompressed .so entries must start at a 16 KB-aligned file offset (zip alignment).

Exit code 0 when everything passes (or no native libraries are present), 1 otherwise.
Uses only the Python standard library.
"""
import struct
import sys
import zipfile

PAGE = 16384


def elf_load_alignments(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is64:
        e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        e_phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x2A)
    result = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        if is64:
            p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
        else:
            p_align = struct.unpack_from(endian + "I", data, off + 0x1C)[0]
        result.append(p_align)
    return result


def local_header_data_offset(path: str, info: zipfile.ZipInfo) -> int:
    with open(path, "rb") as f:
        f.seek(info.header_offset)
        header = f.read(30)
        name_len, extra_len = struct.unpack_from("<HH", header, 26)
        return info.header_offset + 30 + name_len + extra_len


def check(path: str) -> bool:
    ok = True
    found = 0
    is_apk = path.endswith(".apk")
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            name = info.filename
            if not name.endswith(".so") or "lib/" not in name:
                continue
            found += 1
            aligns = elf_load_alignments(z.read(name))
            bad = [a for a in aligns if a < PAGE]
            status = "OK" if not bad else "FAIL"
            if bad:
                ok = False
            msg = f"[{status}] {name}: LOAD p_align={sorted(set(aligns))}"
            if is_apk:
                if info.compress_type == zipfile.ZIP_STORED:
                    data_off = local_header_data_offset(path, info)
                    zip_ok = data_off % PAGE == 0
                    msg += f", stored at offset {data_off} ({'16KB-aligned' if zip_ok else 'NOT 16KB-aligned'})"
                    if not zip_ok:
                        ok = False
                else:
                    msg += ", compressed in APK (extracted at install; zip alignment not applicable)"
            print(msg)
    if found == 0:
        print(f"{path}: no native libraries packaged")
    else:
        print(f"{path}: {found} native libraries checked -> {'PASS' if ok else 'FAIL'}")
    return ok


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: check_elf_alignment.py <file.apk|file.aab> [...]")
        sys.exit(2)
    results = [check(p) for p in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
