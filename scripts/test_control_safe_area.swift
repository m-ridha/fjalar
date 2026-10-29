import Foundation
import CoreGraphics

@main
struct ControlSafeAreaTests {
    static func main() {
        let bounds = CGRect(x: 0, y: 0, width: 960, height: 680)
        let safeFrame = CGRect(x: 0, y: 24, width: 960, height: 632)
        let camera = CGRect(x: 880, y: 0, width: 80, height: 160)
        let fold = CGRect(x: 470, y: 0, width: 20, height: 680)
        let cases: [(String, CGRect, [CGRect])] = [
            ("ordinary safe areas", safeFrame, []),
            ("trailing camera and status region", safeFrame, [camera]),
            ("leading camera in rotated layout", safeFrame, [CGRect(x: 0, y: 0, width: 80, height: 160)]),
            ("partially folded display", safeFrame, [fold]),
            ("fold and camera together", safeFrame, [camera, fold]),
            ("inactive/outside region", safeFrame, [CGRect(x: 1000, y: 0, width: 40, height: 40)]),
        ]
        for (name, safeFrame, regions) in cases {
            let result = controlSafeRect(in: bounds, safeFrame: safeFrame, avoiding: regions)
            precondition(!result.isEmpty, "\(name): controls have no usable region")
            precondition(safeFrame.contains(result), "\(name): controls leave the safe frame")
            for region in regions {
                let overlap = result.intersection(region)
                precondition(overlap.isNull || overlap.isEmpty, "\(name): controls intersect a reserved region")
            }
            if regions.isEmpty { precondition(result == safeFrame) }
        }
        // A horizontal fold should keep controls in one reachable pane too.
        let horizontalFold = CGRect(x: 0, y: 325, width: 960, height: 30)
        let result = controlSafeRect(in: bounds, safeFrame: safeFrame, avoiding: [horizontalFold])
        precondition(result.maxY <= horizontalFold.minY || result.minY >= horizontalFold.maxY)
        // Rotating a portrait corner cutout moves it to the leading landscape edge.
        let portraitBounds = CGRect(x: 0, y: 0, width: 680, height: 960)
        let portraitSafeFrame = CGRect(x: 0, y: 24, width: 680, height: 902)
        let portraitCamera = CGRect(x: 600, y: 0, width: 80, height: 160)
        let portraitResult = controlSafeRect(in: portraitBounds, safeFrame: portraitSafeFrame, avoiding: [portraitCamera])
        precondition(portraitResult == CGRect(x: 0, y: 24, width: 600, height: 902))
        let rotatedCamera = CGRect(x: 0, y: 600, width: 160, height: 80)
        let rotatedResult = controlSafeRect(in: bounds, safeFrame: safeFrame, avoiding: [rotatedCamera])
        precondition(rotatedResult == CGRect(x: 0, y: 24, width: 960, height: 576))

        // Only the portion of a bottom occlusion inside the home-indicator safe frame matters.
        let bottomOcclusion = CGRect(x: 400, y: 620, width: 160, height: 60)
        let bottomResult = controlSafeRect(in: bounds, safeFrame: safeFrame, avoiding: [bottomOcclusion])
        precondition(bottomResult == CGRect(x: 0, y: 24, width: 960, height: 596))
        print("Passed 10 control safe-area scenarios")
    }
}
