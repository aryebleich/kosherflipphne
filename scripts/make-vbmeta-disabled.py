#!/usr/bin/env python3
"""Write an empty AVB vbmeta image with verification disabled (flag 2), like
`avbtool make_vbmeta_image --flags 2 --padding_size 4096`. Used to boot a GSI."""
import struct, sys

out = sys.argv[1] if len(sys.argv) > 1 else "vbmeta-disabled.img"
FORMAT = "!4s2L2QL2Q2Q2Q2Q2QQLL47sx80x"   # AvbVBMetaImageHeader, 256 bytes
assert struct.calcsize(FORMAT) == 256
header = struct.pack(
    FORMAT,
    b"AVB0", 1, 0,          # magic, libavb version 1.0
    0, 0,                   # authentication / auxiliary block sizes
    0,                      # algorithm: NONE
    0, 0, 0, 0,             # hash, signature
    0, 0, 0, 0,             # public key, public key metadata
    0, 0,                   # descriptors
    0,                      # rollback index
    2,                      # flags: AVB_VBMETA_IMAGE_FLAGS_VERIFICATION_DISABLED
    0,                      # rollback index location
    b"avbtool 1.3.0",       # release string
)
with open(out, "wb") as f:
    f.write(header + b"\0" * (4096 - len(header)))
print("wrote", out)
