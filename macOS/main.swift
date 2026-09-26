import Cocoa
import CoreGraphics
import Network

class TrackpadServer {
    private var listener: NWListener?
    private let port: UInt16 = 8080

    func start() {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { return }
        do {
            let params = NWParameters.udp
            params.allowLocalEndpointReuse = true
            listener = try NWListener(using: params, on: nwPort)
            listener?.newConnectionHandler = { [weak self] connection in
                connection.start(queue: .global(qos: .userInteractive))
                self?.receive(on: connection)
            }
            listener?.start(queue: .global())
            NSLog("Trackpad Server listening on UDP port %d", port)
        } catch {
            NSLog("Failed to start listener: %@", error.localizedDescription)
        }
    }

    private func receive(on connection: NWConnection) {
        connection.receiveMessage { [weak self] data, _, _, error in
            guard let self = self else { return }
            if let data = data, let message = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) {
                self.handle(message: message, connection: connection)
            }
            if error == nil {
                self.receive(on: connection)
            }
        }
    }

    private func handle(message: String, connection: NWConnection) {
        if message == "DISCOVER_SERVER" {
            let response = "SERVER_HERE".data(using: .utf8)
            connection.send(content: response, completion: .contentProcessed({ _ in }))
            return
        }

        let parts = message.components(separatedBy: ",")
        guard let action = parts.first else { return }

        switch action {
        case "MOVE":
            if parts.count >= 3, let dx = Double(parts[1]), let dy = Double(parts[2]) {
                moveCursor(dx: CGFloat(dx), dy: CGFloat(dy))
            }
        case "CLICK":
            clickMouse(right: false)
        case "RCLICK":
            clickMouse(right: true)
        case "SCROLL":
            if parts.count >= 3, let dx = Int32(parts[1]), let dy = Int32(parts[2]) {
                scroll(dx: dx, dy: dy)
            }
        default:
            break
        }
    }

    private func moveCursor(dx: CGFloat, dy: CGFloat) {
        let loc = CGEvent(source: nil)?.location ?? .zero
        let target = CGPoint(x: loc.x + dx, y: loc.y + dy)
        let moveEvent = CGEvent(mouseEventSource: nil, mouseType: .mouseMoved, mouseCursorPosition: target, mouseButton: .left)
        moveEvent?.post(tap: .cghidEventTap)
    }

    private func clickMouse(right: Bool) {
        let loc = CGEvent(source: nil)?.location ?? .zero
        let downType: CGEventType = right ? .rightMouseDown : .leftMouseDown
        let upType: CGEventType = right ? .rightMouseUp : .leftMouseUp
        let button: CGMouseButton = right ? .right : .left

        let down = CGEvent(mouseEventSource: nil, mouseType: downType, mouseCursorPosition: loc, mouseButton: button)
        let up = CGEvent(mouseEventSource: nil, mouseType: upType, mouseCursorPosition: loc, mouseButton: button)
        down?.post(tap: .cghidEventTap)
        up?.post(tap: .cghidEventTap)
    }

    private func scroll(dx: Int32, dy: Int32) {
        let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: dy, wheel2: dx, wheel3: 0)
        event?.post(tap: .cghidEventTap)
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem?
    private let server = TrackpadServer()

    func applicationDidFinishLaunching(_ notification: Notification) {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        if !AXIsProcessTrustedWithOptions(options) {
            NSLog("Accessibility permissions required: enable in System Settings > Privacy & Security > Accessibility.")
        }

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem?.button?.title = "📱 Trackpad"

        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "Trackpad Receiver Running", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem?.menu = menu

        server.start()
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
