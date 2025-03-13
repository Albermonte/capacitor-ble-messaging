// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorBleMessaging",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "CapacitorBleMessaging",
            targets: ["BLEMessagingPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "BLEMessagingPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/BLEMessagingPlugin"),
        .testTarget(
            name: "BLEMessagingPluginTests",
            dependencies: ["BLEMessagingPlugin"],
            path: "ios/Tests/BLEMessagingPluginTests")
    ]
)