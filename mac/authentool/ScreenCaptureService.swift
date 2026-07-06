//
//  ScreenCaptureService.swift
//  authentool
//
//  Interactive region screen capture (the system crosshair UI, via /usr/sbin/screencapture) plus
//  QR decoding (Vision). Used to import a 2FA secret by selecting an on-screen otpauth QR code.
//  The screencapture tool handles its own Screen Recording permission prompt.
//

import Foundation
import AppKit
import Vision

enum ScreenCaptureService {
    /// Runs the interactive region screenshot to a temp file and returns the image, or nil if the
    /// user pressed Escape / no selection was made.
    static func captureRegion() async -> CGImage? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("authentool-scan-\(UUID().uuidString).png")
        defer { try? FileManager.default.removeItem(at: url) }

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
        // -i interactive region, -x silent. The path is the capture destination.
        process.arguments = ["-i", "-x", url.path]

        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            process.terminationHandler = { _ in continuation.resume() }
            do { try process.run() } catch { continuation.resume() }
        }

        guard let data = try? Data(contentsOf: url),
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
        else { return nil } // canceled (no file written) or unreadable
        return image
    }

    /// Returns the QR payload strings found in the image (usually 0 or 1).
    static func decodeQRCodes(in image: CGImage) -> [String] {
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        let handler = VNImageRequestHandler(cgImage: image, options: [:])
        try? handler.perform([request])
        return (request.results ?? []).compactMap(\.payloadStringValue)
    }
}
