// Developer-only interop harness. Exposes the AuthentoolCore primitives on the command line
// so scripts/interop_check.sh can prove byte-for-byte compatibility with cli/crypt.py and
// cli/totp_gen.py. NOT part of the shipped macOS app.
//
//   interop-cli encrypt <password>        plaintext on stdin  -> blob on stdout
//   interop-cli decrypt <password>        blob on stdin       -> plaintext on stdout
//   interop-cli totp <base32seed> <unix>  -> 6-digit code on stdout

import Foundation
import AuthentoolCore

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

func readStdin() -> String {
    let data = FileHandle.standardInput.readDataToEndOfFile()
    return String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
}

let args = Array(CommandLine.arguments.dropFirst())
guard let command = args.first else {
    fail("usage: interop-cli <encrypt|decrypt|totp> ...")
}

do {
    switch command {
    case "encrypt":
        guard args.count >= 2 else { fail("usage: interop-cli encrypt <password>") }
        print(try CryptoBox.encrypt(readStdin(), password: args[1]))

    case "decrypt":
        guard args.count >= 2 else { fail("usage: interop-cli decrypt <password>") }
        print(try CryptoBox.decrypt(readStdin(), password: args[1]))

    case "totp":
        guard args.count >= 3, let unix = Double(args[2]) else {
            fail("usage: interop-cli totp <base32seed> <unixtime>")
        }
        let generator = try TOTPGenerator(base32Secret: args[1])
        print(generator.code(at: Date(timeIntervalSince1970: unix)))

    default:
        fail("unknown command: \(command)")
    }
} catch {
    fail("error: \(error)")
}
