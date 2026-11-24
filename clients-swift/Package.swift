// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "clients-swift",
    platforms: [.macOS(.v15)],
    dependencies: [
        .package(url: "https://github.com/vapor/vapor.git", from: "4.115.0"),
        .package(url: "https://github.com/vapor/postgres-nio.git", from: "1.26.0"),
        .package(url: "https://github.com/vapor/jwt-kit.git", from: "5.1.0"),
    ],
    targets: [
        .executableTarget(
            name: "ClientsService",
            dependencies: [
                .product(name: "Vapor", package: "vapor"),
                .product(name: "PostgresNIO", package: "postgres-nio"),
                .product(name: "JWTKit", package: "jwt-kit"),
            ]
        ),
        .testTarget(
            name: "ClientsServiceTests",
            dependencies: [.target(name: "ClientsService")]
        ),
    ]
)
