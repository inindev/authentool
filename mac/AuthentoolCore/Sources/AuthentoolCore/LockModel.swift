import Foundation
import Observation

// Owns the app's LockState and drives authentication through an injected authenticator, so the
// state machine is testable with a fake while the app supplies a LocalAuthentication-backed one.
//
// Transitions:
//   locked --authenticate()--> authenticating --(success)--> unlocked
//                                             \--(failure)--> locked
//   <any non-authenticating> --lock()--> locked

/// Performs the actual user verification. The app implements this with LocalAuthentication;
/// tests inject a stub.
public protocol BiometricAuthenticator: Sendable {
    /// Prompts the user. Returns true on success; false on failure, cancel, or unavailability.
    func authenticate(reason: String) async -> Bool
}

@MainActor
@Observable
public final class LockModel {
    public private(set) var state: LockState = .locked

    /// When true, a resign-active should NOT re-lock - used while the app itself drives a system
    /// overlay (e.g. the interactive screen-capture for QR scanning) that transiently steals focus.
    @ObservationIgnored public var suppressAutoLock = false

    private let authenticator: BiometricAuthenticator
    private let reason: String

    public init(authenticator: BiometricAuthenticator, reason: String = "Unlock Authentool") {
        self.authenticator = authenticator
        self.reason = reason
    }

    public var isUnlocked: Bool { state == .unlocked }

    /// Runs an authentication attempt. No-op unless currently '.locked', so concurrent triggers
    /// (launch + return-to-foreground) and re-entrancy can't stack prompts.
    public func authenticate() async {
        guard state == .locked else { return }
        state = .authenticating
        let success = await authenticator.authenticate(reason: reason)
        // A lock() during the prompt (e.g. app resigned active) wins: only unlock if we're still
        // the in-flight attempt.
        guard state == .authenticating else { return }
        state = success ? .unlocked : .locked
    }

    /// Re-locks (on launch already locked, or on resign-active / occlusion). Ignored mid-prompt
    /// so an in-flight authentication isn't torn down underneath itself.
    public func lock() {
        guard state != .authenticating else { return }
        state = .locked
    }
}
