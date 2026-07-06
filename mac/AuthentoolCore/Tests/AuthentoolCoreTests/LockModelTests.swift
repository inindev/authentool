import Testing
import Foundation
@testable import AuthentoolCore

// A stub authenticator returning a fixed (mutable) result, with no real prompt.
private final class StubAuthenticator: BiometricAuthenticator, @unchecked Sendable {
    var result: Bool
    init(result: Bool) { self.result = result }
    func authenticate(reason: String) async -> Bool { result }
}

// A stub that runs a hook in the middle of the prompt, to exercise re-entrancy.
private final class HookAuthenticator: BiometricAuthenticator, @unchecked Sendable {
    var result: Bool
    var onAuthenticate: (@Sendable () async -> Void)?
    init(result: Bool) { self.result = result }
    func authenticate(reason: String) async -> Bool {
        await onAuthenticate?()
        return result
    }
}

@Suite("Lock state machine")
@MainActor
struct LockModelTests {
    @Test func startsLocked() {
        let model = LockModel(authenticator: StubAuthenticator(result: true))
        #expect(model.state == .locked)
        #expect(!model.isUnlocked)
    }

    @Test func successUnlocks() async {
        let model = LockModel(authenticator: StubAuthenticator(result: true))
        await model.authenticate()
        #expect(model.state == .unlocked)
        #expect(model.isUnlocked)
    }

    @Test func failureStaysLocked() async {
        let model = LockModel(authenticator: StubAuthenticator(result: false))
        await model.authenticate()
        #expect(model.state == .locked)
    }

    @Test func lockReturnsToLocked() async {
        let model = LockModel(authenticator: StubAuthenticator(result: true))
        await model.authenticate()
        #expect(model.state == .unlocked)
        model.lock()
        #expect(model.state == .locked)
    }

    @Test func authenticateIsNoOpWhenAlreadyUnlocked() async {
        let stub = StubAuthenticator(result: true)
        let model = LockModel(authenticator: stub)
        await model.authenticate()
        #expect(model.state == .unlocked)
        // A second attempt that would fail must not re-lock an unlocked app.
        stub.result = false
        await model.authenticate()
        #expect(model.state == .unlocked)
    }

    @Test func lockIsIgnoredMidPrompt() async {
        // A lock() arriving while a prompt is in flight is ignored, so a successful prompt still
        // unlocks (the app stays in control of the prompt it started).
        let hook = HookAuthenticator(result: true)
        let model = LockModel(authenticator: hook)
        hook.onAuthenticate = { @MainActor in model.lock() }
        await model.authenticate()
        #expect(model.state == .unlocked)
    }
}
