//
//  SystemBiometricAuthenticator.swift
//  authentool
//
//  The production BiometricAuthenticator: Touch ID via LocalAuthentication, falling back
//  to the device password policy when biometrics are unavailable. Any failure/cancel/unavailable
//  resolves to 'false', leaving the app locked.
//

import Foundation
import LocalAuthentication
import AuthentoolCore

struct SystemBiometricAuthenticator: BiometricAuthenticator {
    func authenticate(reason: String) async -> Bool {
        let context = LAContext()

        // '.deviceOwnerAuthentication' shows Touch ID first and makes the "Use Password..." button
        // actually present the login-password sheet (the biometrics-only policy returns a
        // userFallback error instead, so the fallback button does nothing). It also covers Macs
        // with no/unenrolled biometrics by going straight to the password.
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            return false
        }

        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
