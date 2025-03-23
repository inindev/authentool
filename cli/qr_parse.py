#!/usr/bin/env python3
#
# decodes a QR code from an image file and prints its resulting TOTP url components
# depends: pip3 install opencv-python
#
# Copyright (c) 2025, John Clark <inindev@gmail.com>
# Licensed under the Apache License. See LICENSE file in the project root for full license information.
#

import cv2
import sys
from urllib.parse import urlparse, parse_qs

# set OpenCV logging level to error (suppresses warnings)
cv2.setLogLevel(1)

def decode_qr_code(input_file):
    """
    Decode a QR code from an image file and return the extracted text (e.g., TOTP URL).

    Args:
        input_file (str): Path to the image file containing the QR code.

    Returns:
        str: The decoded text from the QR code.

    Raises:
        FileNotFoundError: If the input file doesn't exist or can't be read.
        ValueError: If no QR code is found in the image.
    """
    # load image with OpenCV
    img = cv2.imread(input_file)
    if img is None:
        raise FileNotFoundError(f"Cannot read file: {input_file}")

    # create qr code detector and decode
    detector = cv2.QRCodeDetector()
    data, _, _ = detector.detectAndDecode(img)

    if not data:
        raise ValueError(f"No QR code found in image: {input_file}")

    return data

def parse_totp_url(url):
    """
    Parse a TOTP URL into its components.

    Args:
        url (str): The TOTP URL (e.g., otpauth://totp/acme:testuser?secret=...).

    Returns:
        dict: A dictionary with 'name', 'issuer', 'secret', and 'url'.

    Raises:
        ValueError: If the URL is not a valid otpauth TOTP URL.
    """
    # check if it is an otpauth url with totp type
    if not url.startswith("otpauth://totp/"):
        raise ValueError("URL must be an otpauth://totp/ URI")

    # parse the url
    parsed = urlparse(url)

    # extract the name (path after /totp/)
    name = parsed.path.lstrip("/totp/")
    if not name:
        raise ValueError("TOTP URL must include a name/label")

    # parse query parameters
    params = parse_qs(parsed.query)

    # extract secret
    secret = params.get("secret", [None])[0]
    if not secret:
        raise ValueError("TOTP URL must include a secret parameter")

    # extract issuer (optional, defaults to none if not present)
    issuer = params.get("issuer", [None])[0]

    # return components in a dictionary
    return {
        "name": name,
        "issuer": issuer,
        "secret": secret,
        "url": url
    }

def main():
    if len(sys.argv) < 2:
        print("Usage: python qr_decode.py <input_image>")
        sys.exit(1)

    input_file = sys.argv[1]
    try:
        url = decode_qr_code(input_file)
        parsed = parse_totp_url(url)

        print(f"name:   {parsed['name']}")
        print(f"issuer: {parsed['issuer']}")
        print(f"seed:   {parsed['secret']}")
        print(f"url:    {parsed['url']}")
    except (FileNotFoundError, ValueError) as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
