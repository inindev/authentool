#!/usr/bin/env python3
#
# command-line tool for AES-GCM encryption/decryption
#
# Copyright (c) 2025, John Clark <inindev@gmail.com>
# Licensed under the Apache License. See LICENSE file in the project root for full license information.
#

import sys
import base64
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag
import os
import getpass


SALT_SIZE = 16        # 16-byte salt
IV_SIZE = 12          # 12-byte IV for AES-GCM
KEY_SIZE = 32         # 256-bit key (AES-256)
TAG_SIZE = 16         # 128-bit (16-byte) GCM tag
VERSION_ID = 0x08     # version byte
ITERATIONS = 250_000  # PBKDF2 iterations

class CryptException(Exception):
    pass

def derive_key(password: str, salt: bytes) -> bytes:
    """Derive a key from password and salt using PBKDF2."""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=KEY_SIZE,
        salt=salt,
        iterations=ITERATIONS,
    )
    return kdf.derive(password.encode('utf-8'))

def encrypt(plaintext: str, password: str) -> str:
    """Encrypt plaintext using AES-GCM with the provided password."""
    if not plaintext or not password:
        raise CryptException("Plaintext and password cannot be empty")

    # generate salt and IV
    salt = os.urandom(SALT_SIZE)
    iv = os.urandom(IV_SIZE)

    # derive key
    key = derive_key(password, salt)

    # encrypt
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(iv, plaintext.encode('utf-8'), None)

    # combine version ID, salt, IV, and ciphertext (includes tag)
    output = bytes([VERSION_ID]) + salt + iv + ciphertext

    # base64 encode
    return base64.b64encode(output).decode('utf-8')

def decrypt(base64_encrypted: str, password: str) -> str:
    """Decrypt base64-encoded encrypted string using the provided password."""
    try:
        # ensure proper base64 padding
        missing_padding = len(base64_encrypted) % 4
        if missing_padding:
            base64_encrypted += '=' * (4 - missing_padding)

        # decode base64
        encrypted_bytes = base64.b64decode(base64_encrypted)

        # check minimum length
        min_size = 1 + SALT_SIZE + IV_SIZE + TAG_SIZE + 1  # Version + salt + IV + tag + min ciphertext
        if len(encrypted_bytes) < min_size:
            raise CryptException("Encrypted data too short")

        # extract components
        version = encrypted_bytes[0]
        if version != VERSION_ID:
            raise CryptException(f"Unsupported version ID: {version:02x}")

        salt = encrypted_bytes[1:1 + SALT_SIZE]
        iv = encrypted_bytes[1 + SALT_SIZE:1 + SALT_SIZE + IV_SIZE]
        ciphertext = encrypted_bytes[1 + SALT_SIZE + IV_SIZE:]

        # derive key
        key = derive_key(password, salt)

        # decrypt
        aesgcm = AESGCM(key)
        plaintext = aesgcm.decrypt(iv, ciphertext, None)

        return plaintext.decode('utf-8')
    except InvalidTag:
        raise CryptException("Decryption failed: Invalid tag (wrong password?)")
    except Exception as e:
        raise CryptException(f"Decryption failed: {str(e)}")

def get_verified_password() -> str:
    """Prompt for password twice and verify they match."""
    while True:
        password1 = getpass.getpass("Enter password: ")
        password2 = getpass.getpass("Confirm password: ")
        if password1 == password2:
            return password1
        print("Passwords do not match. Please try again.", file=sys.stderr)

def main():
    if len(sys.argv) < 2 or len(sys.argv) > 3:
        print(f"\nUsage: {sys.argv[0]} {{encrypt|decrypt}} [<string>]")
        print("\nIf no string is provided as an argument, input will be read from stdin.\n")
        sys.exit(1)

    if len(sys.argv) > 2:
        input_data = sys.argv[2]
    elif not sys.stdin.isatty():
        input_data = sys.stdin.read().strip()
    else:
        print("Error: No input provided (use argument or pipe data to stdin)", file=sys.stderr)
        sys.exit(1)

    try:
        mode = sys.argv[1]
        if mode == "encrypt":
            password = get_verified_password()  # get password with verification
            encrypted = encrypt(input_data, password)
            print(encrypted)
        elif mode == "decrypt":
            password = getpass.getpass("Enter password: ")  # single prompt for decrypt
            decrypted = decrypt(input_data, password)
            print(decrypted)
        else:
            print("Invalid mode. Use 'encrypt' or 'decrypt'.")
            sys.exit(1)
    except CryptException as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
