import Testing
import Foundation
@testable import AuthentoolCore

@Suite("TOTP window / countdown")
struct TOTPWindowTests {
    private func date(_ unix: TimeInterval) -> Date { Date(timeIntervalSince1970: unix) }

    @Test func counterMatchesTimeStepBuckets() {
        #expect(TOTPWindow.counter(at: date(0)) == 0)
        #expect(TOTPWindow.counter(at: date(29)) == 0)
        #expect(TOTPWindow.counter(at: date(30)) == 1)
        #expect(TOTPWindow.counter(at: date(1234567890)) == 41152263)
    }

    @Test func remainingFractionIsFullAtWindowStart() {
        #expect(abs(TOTPWindow.remainingFraction(at: date(30)) - 1.0) < 1e-9)
        #expect(abs(TOTPWindow.remainingFraction(at: date(60)) - 1.0) < 1e-9)
    }

    @Test func remainingFractionDecreasesThroughWindow() {
        // 15s into a 30s window -> half remaining.
        #expect(abs(TOTPWindow.remainingFraction(at: date(15)) - 0.5) < 1e-9)
        // 29s in -> ~1/30 remaining.
        #expect(abs(TOTPWindow.remainingFraction(at: date(29)) - (1.0 / 30.0)) < 1e-9)
    }

    @Test func remainingFractionStaysInRange() {
        for t in stride(from: 0.0, to: 120.0, by: 0.37) {
            let f = TOTPWindow.remainingFraction(at: date(t))
            #expect(f > 0.0 && f <= 1.0)
        }
    }
}
