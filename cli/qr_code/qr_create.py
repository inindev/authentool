#!/usr/bin/env python3
#
# Encodes a TOTP URL into a QR code and saves it as an image file with configurable resolution
# depends: pip3 install opencv-python
#
# Copyright (c) 2025, John Clark <inindev@gmail.com>
# Licensed under the Apache License. See LICENSE file in the project root for full license information.
#

import cv2
import sys
import argparse

# set OpenCV logging level to error (suppresses warnings)
cv2.setLogLevel(1)

def create_qr_code(totp_url, output_file, size=200):
    """
    Encode a TOTP URL into a QR code and save it as an image file with specified resolution.

    Args:
        totp_url (str): The TOTP URL to encode (e.g., otpauth://totp/acme:testuser?secret=...).
        output_file (str): Path to save the generated QR code image.
        size (int): The desired width and height of the output image in pixels (default: 200).

    Returns:
        None

    Raises:
        ValueError: If the TOTP URL is invalid, empty, or size is invalid.
        RuntimeError: If the QR code cannot be generated or saved.
    """
    # validate inputs
    if not totp_url or not isinstance(totp_url, str):
        raise ValueError("TOTP URL must be a non-empty string")
    if not totp_url.startswith("otpauth://totp/"):
        raise ValueError("URL must be an otpauth://totp/ URI")
    if not isinstance(size, int) or size <= 0:
        raise ValueError("Size must be a positive integer")

    # create QR code encoder
    encoder = cv2.QRCodeEncoder.create()

    # encode the TOTP URL into a QR code matrix
    qr_matrix = encoder.encode(totp_url)
    if qr_matrix is None:
        raise RuntimeError("Failed to generate QR code from the provided URL")

    # resize the QR code matrix to the desired size
    resized_qr = cv2.resize(qr_matrix, (size, size), interpolation=cv2.INTER_NEAREST)

    # save the QR code as an image file
    success = cv2.imwrite(output_file, resized_qr, [cv2.IMWRITE_PNG_COMPRESSION, 9])
    if not success:
        raise RuntimeError(f"Failed to save QR code to file: {output_file}")

def main():
    parser = argparse.ArgumentParser(
        description="Encode a TOTP URL into a QR code image.",
        epilog="Example: python qr_create.py 'otpauth://totp/acme:testuser?secret=ABC123&issuer=acme' output.png --size 300"
    )
    parser.add_argument("totp_url", help="The TOTP URL to encode")
    parser.add_argument("output_file", help="The output image file path (e.g., qr.png)")
    parser.add_argument("--size", type=int, default=200, help="Output image size in pixels (default: 200)")

    args = parser.parse_args()

    try:
        create_qr_code(args.totp_url, args.output_file, args.size)
        print(f"QR code successfully saved to: {args.output_file} (size: {args.size}x{args.size} pixels)")
    except (ValueError, RuntimeError) as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
