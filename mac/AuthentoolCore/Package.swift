// swift-tools-version: 6.0
import PackageDescription

// AuthentoolCore holds the deterministic logic of the app - Base32, TOTP, the crypto blob
// (PBKDF2 + AES-GCM), the domain model, and the entry store - all free of SwiftUI and
// LocalAuthentication and 100% unit-testable against the RFC 6238 / RFC 4648 golden vectors.
// The one I/O adapter is KeychainEntryStorage (Foundation + Security only), kept behind the
// EntryStorage protocol so the store is tested with an in-memory fake.
let package = Package(
    name: "AuthentoolCore",
    platforms: [.macOS(.v14)],
    products: [
        .library(name: "AuthentoolCore", targets: ["AuthentoolCore"]),
        // Developer-only tool used by scripts/interop_check.sh to prove byte-compatibility
        // with the Android app and CLI tools. Not referenced by the macOS app target.
        .executable(name: "interop-cli", targets: ["interop-cli"]),
    ],
    targets: [
        .target(name: "AuthentoolCore"),
        .executableTarget(name: "interop-cli", dependencies: ["AuthentoolCore"]),
        .testTarget(
            name: "AuthentoolCoreTests",
            dependencies: ["AuthentoolCore"]
        ),
    ]
)
