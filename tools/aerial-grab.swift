// Usage (via tools/aerial-wallpapers.sh): aerial-grab <video url> <out.jpg> <fraction 0-1>
// Grabs one frame via AVFoundation (HTTP range reads, not a full download) and writes a 1920x1080 JPEG.
import AVFoundation
import AppKit
let a = CommandLine.arguments
let asset = AVURLAsset(url: URL(string: a[1])!)
let gen = AVAssetImageGenerator(asset: asset)
gen.appliesPreferredTrackTransform = true
gen.maximumSize = CGSize(width: 1920, height: 1080)
gen.requestedTimeToleranceBefore = CMTime(seconds: 2, preferredTimescale: 600)
gen.requestedTimeToleranceAfter = CMTime(seconds: 2, preferredTimescale: 600)
let sem = DispatchSemaphore(value: 0)
var code: Int32 = 1
Task {
    do {
        let dur = try await asset.load(.duration)
        let t = CMTimeMultiplyByFloat64(dur, multiplier: Double(a[3])!)
        let (img, _) = try await gen.image(at: t)
        let rep = NSBitmapImageRep(cgImage: img)
        try rep.representation(using: .jpeg, properties: [.compressionFactor: 0.85])!.write(to: URL(fileURLWithPath: a[2]))
        code = 0
    } catch { FileHandle.standardError.write("\(error)\n".data(using: .utf8)!) }
    sem.signal()
}
sem.wait()
exit(code)
