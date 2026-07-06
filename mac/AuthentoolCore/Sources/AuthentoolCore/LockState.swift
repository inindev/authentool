import Foundation

// The app's lock state. A small, explicit three-state machine with a single
// owner (LockModel) - no scattered booleans. While not '.unlocked', the UI must obscure the
// codes entirely.
public enum LockState: Equatable, Sendable {
    case locked         // codes hidden; awaiting a (re)authentication
    case authenticating // a biometric/password prompt is in flight
    case unlocked       // codes visible
}
