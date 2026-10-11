import AppKit
import Foundation

guard CommandLine.arguments.count >= 3 else {
    fputs("Usage: IconDraw.swift <size> <output.png>\n", stderr)
    exit(1)
}

let size = Int(CommandLine.arguments[1]) ?? 1024
let outPath = CommandLine.arguments[2]
let s = CGFloat(size)

let image = NSImage(size: NSSize(width: s, height: s))
image.lockFocus()

let inset = s / 16.0
let rounded = NSBezierPath(
    roundedRect: NSRect(x: inset, y: inset, width: s - inset * 2, height: s - inset * 2),
    xRadius: s / 5.0,
    yRadius: s / 5.0)
NSColor(red: 0.357, green: 0.549, blue: 1.0, alpha: 1.0).setFill()
rounded.fill()

let play = NSBezierPath()
play.move(to: NSPoint(x: s * 0.38, y: s * 0.28))
play.line(to: NSPoint(x: s * 0.38, y: s * 0.72))
play.line(to: NSPoint(x: s * 0.72, y: s * 0.50))
play.close()
NSColor(red: 0.043, green: 0.063, blue: 0.125, alpha: 1.0).setFill()
play.fill()

image.unlockFocus()

guard let tiff = image.tiffRepresentation,
      let rep = NSBitmapImageRep(data: tiff),
      let png = rep.representation(using: .png, properties: [:]) else {
    fputs("Failed to encode PNG\n", stderr)
    exit(1)
}

do {
    try png.write(to: URL(fileURLWithPath: outPath))
} catch {
    fputs("Write failed: \(error)\n", stderr)
    exit(1)
}
