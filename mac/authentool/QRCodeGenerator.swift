//
//  QRCodeGenerator.swift
//  authentool
//
//  Renders an entry's otpauth:// URI as a QR-code NSImage for the printable backup sheet.
//

import Foundation
import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import AuthentoolCore

enum QRCodeGenerator {
    private static let context = CIContext()

    /// A crisp black-on-white QR image for the entry, or nil if generation fails.
    static func image(for entry: AuthEntry, side: CGFloat = 480) -> NSImage? {
        let uri = OTPAuthURL.makeURI(name: entry.name, secret: entry.seed)
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(uri.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }

        // Scale the (small) QR up to the requested pixel size with no smoothing.
        let scale = side / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return NSImage(cgImage: cgImage, size: NSSize(width: side, height: side))
    }
}
