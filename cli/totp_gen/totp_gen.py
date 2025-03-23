#!/usr/bin/env python3
#
# generates TOTP codes from a base32 seed (seed.txt)
#
# Copyright (c) 2025, John Clark <inindev@gmail.com>
#
# Licensed under the Apache License. See LICENSE file in the project root for full license information.
#

import base64
import hmac
import os
import stat
import sys
import time
from datetime import datetime

def base32_decode(base32: str) -> bytes:
    """Decode a Base32 string to bytes per RFC 4648."""
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    bits = 0
    bit_count = 0
    bytes_list = []

    for char in base32.upper().replace("=", ""):
        try:
            value = alphabet.index(char)
        except ValueError as e:
            raise ValueError(f"Invalid base32 character: {char}") from e

        bits = (bits << 5) | value
        bit_count += 5

        if bit_count >= 8:
            bit_count -= 8
            bytes_list.append((bits >> bit_count) & 0xFF)

    return bytes(bytes_list)

def read_secret_bytes(filename: str = "seed.txt") -> bytes:
    """Read and validate seed.txt, returning decoded secret bytes."""
    if not os.path.isfile(filename):
        raise FileNotFoundError(f"{filename} not found in current directory")

    # check seet.txt file permissions
    mode = os.stat(filename).st_mode & 0o777
    valid_perms = {stat.S_IRUSR | stat.S_IWUSR, stat.S_IRUSR}  # 600 or 400
    if mode not in valid_perms:
        raise PermissionError(f"{filename} has insecure permissions (must be 600 or 400)")

    with open(filename, "r", encoding="utf-8") as f:
        secret = f.read().strip()

    if not secret:
        raise ValueError(f"{filename} is empty")

    return base32_decode(secret)

def generate_totp(secret_bytes: bytes, timestamp: float = None) -> str:
    """Generate a TOTP code per RFC 6238 with HMAC-SHA1."""
    time_step = 30
    digits = 6
    timestamp = timestamp or time.time()
    counter = int(timestamp) // time_step
    counter_bytes = counter.to_bytes(8, "big")

    hmac_obj = hmac.new(secret_bytes, counter_bytes, "sha1")
    hash_bytes = hmac_obj.digest()

    offset = hash_bytes[-1] & 0x0F
    binary = (
        (hash_bytes[offset] & 0x7F) << 24 |
        (hash_bytes[offset + 1] & 0xFF) << 16 |
        (hash_bytes[offset + 2] & 0xFF) << 8 |
        (hash_bytes[offset + 3] & 0xFF)
    )
    return f"{binary % 10**digits:06d}"

def main():
    try:
        secret_bytes = read_secret_bytes()
    except (FileNotFoundError, PermissionError, ValueError) as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

    print("\nAuthenticator codes (Ctrl+C to exit)")
    try:
        last_length = 0
        while True:
            now = datetime.now()
            code = generate_totp(secret_bytes)
            time_str = now.strftime("%H:%M:%S")
            seconds_left = 30 - (int(time.time()) % 30)
            dots = "." * min((seconds_left + 2) // 3, 10)
            output = f"{time_str}:  {code[:3]} {code[3:]}  {dots}"
            print(f"\r{output:<{max(len(output), last_length)}}", end="", flush=True)
            last_length = len(output)
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down gracefully...", file=sys.stderr)
        sys.exit(0)

if __name__ == "__main__":
    main()
