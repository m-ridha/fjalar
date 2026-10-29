import SwiftUI
import KotlinModules

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> some UIViewController {
        ControlSafeAreaViewController(content: ViewControllerKt.ViewController())
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea()
    }
}

@main
struct iosApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Leaves Compose's canvas edge-to-edge while exposing reserved regions through
/// the child controller's safe area, which interactive Compose surfaces consume.
private final class ControlSafeAreaViewController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        view.addSubview(content.view)
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        content.view.frame = view.bounds
        content.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateControlSafeArea()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateControlSafeArea()
    }

    #if compiler(>=6.4)
    @available(iOS 26.0, *)
    override func updateProperties() {
        super.updateProperties()
        // UIKit tracks reserved-region queries here and invalidates when they change.
        updateControlSafeArea()
    }

    #endif

    private func updateControlSafeArea() {
        guard isViewLoaded, !view.bounds.isEmpty else { return }
        var regions: [CGRect] = []
        #if compiler(>=6.4)
        if #available(iOS 27.1, *) {
            let occlusions = view.reservedRegions(kind: .occlusion)
                .filter { $0.isActive }.map { $0.frame }
            let folds = view.reservedRegions(kind: .division)
                .filter { $0.isActive }.map { region in
                    let frame = region.frame
                    // Even a zero-width separating fold needs touch clearance.
                    return frame.insetBy(
                        dx: frame.width < frame.height ? -16 : 0,
                        dy: frame.width < frame.height ? 0 : -16
                    )
                }
            regions = occlusions + folds
        }
        #endif
        let baseInsets = view.safeAreaInsets
        let frame = controlSafeRect(
            in: view.bounds,
            safeFrame: view.bounds.inset(by: baseInsets),
            avoiding: regions
        )
        let extraInsets = UIEdgeInsets(
            top: max(0, frame.minY - view.bounds.minY - baseInsets.top),
            left: max(0, frame.minX - view.bounds.minX - baseInsets.left),
            bottom: max(0, view.bounds.maxY - frame.maxY - baseInsets.bottom),
            right: max(0, view.bounds.maxX - frame.maxX - baseInsets.right)
        )
        if content.additionalSafeAreaInsets != extraInsets {
            content.additionalSafeAreaInsets = extraInsets
        }
    }
}
