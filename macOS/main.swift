import Cocoa
import CoreGraphics
import Network
import ScreenCaptureKit
import ImageIO
import UniformTypeIdentifiers

func getLocalIPAddresses() -> [String] {
    var addresses = [String]()
    var ifaddr: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return [] }
    defer { freeifaddrs(ifaddr) }

    for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
        let interface = ptr.pointee
        let addrFamily = interface.ifa_addr.pointee.sa_family
        if addrFamily == UInt8(AF_INET) {
            let name = String(cString: interface.ifa_name)
            if name != "lo0" {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                            &hostname, socklen_t(hostname.count),
                            nil, socklen_t(0), NI_NUMERICHOST)
                let ip = String(cString: hostname)
                addresses.append("\(name): \(ip)")
            }
        }
    }
    return addresses
}

func getInstalledApps() -> [String] {
    let fm = FileManager.default
    let dirs = ["/Applications", "/System/Applications", "/System/Applications/Utilities"]
    var apps = Set<String>()
    for dir in dirs {
        if let items = try? fm.contentsOfDirectory(atPath: dir) {
            for item in items where item.hasSuffix(".app") {
                let name = (item as NSString).deletingPathExtension
                apps.insert(name)
            }
        }
    }
    return apps.sorted()
}

struct CachedTab {
    let id: Int
    let type: String
    let appName: String
    let title: String
    let target1: String
    let target2: String
}

var cachedTabs: [Int: CachedTab] = [:]
let tabLock = NSLock()

func refreshOpenWindowsAndTabs() -> String {
    let script = """
    set res to {}

    try
        tell application "System Events" to set isChrome to exists (application processes where name is "Google Chrome")
        if isChrome then
            tell application "Google Chrome"
                repeat with w in windows
                    set wId to id of w as string
                    set tIdx to 1
                    repeat with t in tabs of w
                        set end of res to "chrome|||Google Chrome|||" & (title of t) & "|||" & wId & "|||" & (tIdx as string)
                        set tIdx to tIdx + 1
                    end repeat
                end repeat
            end tell
        end if
    end try

    try
        tell application "System Events" to set isBrave to exists (application processes where name is "Brave Browser")
        if isBrave then
            tell application "Brave Browser"
                repeat with w in windows
                    set wId to id of w as string
                    set tIdx to 1
                    repeat with t in tabs of w
                        set end of res to "brave|||Brave Browser|||" & (title of t) & "|||" & wId & "|||" & (tIdx as string)
                        set tIdx to tIdx + 1
                    end repeat
                end repeat
            end tell
        end if
    end try

    try
        tell application "System Events" to set isSafari to exists (application processes where name is "Safari")
        if isSafari then
            tell application "Safari"
                set wIdx to 1
                repeat with w in windows
                    set tIdx to 1
                    repeat with t in tabs of w
                        set end of res to "safari|||Safari|||" & (name of t) & "|||" & (wIdx as string) & "|||" & (tIdx as string)
                        set tIdx to tIdx + 1
                    end repeat
                    set wIdx to wIdx + 1
                end repeat
            end tell
        end if
    end try

    tell application "System Events"
        set appList to every application process
        repeat with p in appList
            try
                if visible of p is true then
                    set pName to name of p
                    if pName is not in {"Google Chrome", "Brave Browser", "Safari"} then
                        set wList to every window of p
                        repeat with w in wList
                            set wName to name of w
                            if wName is not "" then
                                set end of res to "window|||" & pName & "|||" & wName & "|||" & pName & "|||" & wName
                            end if
                        end repeat
                    end if
                end if
            end try
        end repeat
    end tell

    set AppleScript's text item delimiters to "###"
    return res as string
    """

    let task = Process()
    task.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
    task.arguments = ["-e", script]
    let pipe = Pipe()
    task.standardOutput = pipe
    try? task.run()
    task.waitUntilExit()
    let data = pipe.fileHandleForReading.readDataToEndOfFile()
    let raw = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

    tabLock.lock()
    defer { tabLock.unlock() }
    cachedTabs.removeAll()

    var clientList = [String]()
    var currentId = 0
    let items = raw.components(separatedBy: "###")
    for item in items {
        let parts = item.components(separatedBy: "|||")
        if parts.count >= 5 {
            let type = parts[0]
            let appName = parts[1]
            let title = parts[2]
            let target1 = parts[3]
            let target2 = parts[4]
            cachedTabs[currentId] = CachedTab(id: currentId, type: type, appName: appName, title: title, target1: target1, target2: target2)
            clientList.append("\(currentId)|||AppName:\(appName)|||Title:\(title)|||Type:\(type)")
            currentId += 1
        }
    }
    return clientList.joined(separator: "###")
}

func doSwitchTab(id: Int) {
    tabLock.lock()
    guard let item = cachedTabs[id] else {
        tabLock.unlock()
        print("[Switch] Tab id \(id) not found in cache")
        return
    }
    let type = item.type
    let appName = item.appName
    let title = item.title
    let target1 = item.target1
    let target2 = item.target2
    tabLock.unlock()

    print("[Switch] Switching to [\(type)] \(appName) - \(title)")
    var script = ""
    if type == "chrome" || type == "brave" {
        script = """
        tell application "\(appName)"
            set index of window id \(target1) to 1
            set active tab index of window id \(target1) to \(target2)
            activate
        end tell
        """
    } else if type == "safari" {
        script = """
        tell application "Safari"
            set index of window \(target1) to 1
            set current tab of window \(target1) to tab \(target2) of window \(target1)
            activate
        end tell
        """
    } else if type == "window" {
        let cleanTitle = target2.replacingOccurrences(of: "\"", with: "\\\"")
        script = """
        tell application "System Events"
            tell process "\(target1)"
                set frontmost to true
                try
                    perform action "AXRaise" of (first window whose name is "\(cleanTitle)")
                end try
            end tell
        end tell
        """
    }

    if !script.isEmpty {
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        task.arguments = ["-e", script]
        try? task.run()
        task.waitUntilExit()
        print("[Switch] Completed switch with code \(task.terminationStatus)")
    }
}

func launchApp(name: String) {
    let task = Process()
    task.executableURL = URL(fileURLWithPath: "/usr/bin/open")
    task.arguments = ["-a", name]
    do {
        try task.run()
        print("[App] Successfully launched: \(name)")
    } catch {
        print("[App] Failed to launch \(name): \(error.localizedDescription)")
    }
}

func sendKeyCombo(virtualKey: CGKeyCode, flags: CGEventFlags) {
    let src = CGEventSource(stateID: .hidSystemState)
    let down = CGEvent(keyboardEventSource: src, virtualKey: virtualKey, keyDown: true)
    down?.flags = flags
    let up = CGEvent(keyboardEventSource: src, virtualKey: virtualKey, keyDown: false)
    down?.post(tap: .cghidEventTap)
    up?.post(tap: .cghidEventTap)
}

func runAppleScript(_ script: String) {
    let task = Process()
    task.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
    task.arguments = ["-e", script]
    try? task.run()
}

func handleMacAction(_ action: String) {
    switch action {
    case "MISSION_CONTROL":
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        task.arguments = ["-a", "Mission Control"]
        try? task.run()
    case "SPOTLIGHT":
        sendKeyCombo(virtualKey: 49, flags: .maskCommand) // Cmd + Space
    case "DESKTOP":
        sendKeyCombo(virtualKey: 103, flags: []) // F11
    case "CLOSE_WINDOW":
        sendKeyCombo(virtualKey: 13, flags: .maskCommand) // Cmd + W
    case "QUIT_APP":
        sendKeyCombo(virtualKey: 12, flags: .maskCommand) // Cmd + Q
    case "COPY":
        sendKeyCombo(virtualKey: 8, flags: .maskCommand) // Cmd + C
    case "PASTE":
        sendKeyCombo(virtualKey: 9, flags: .maskCommand) // Cmd + V
    case "UNDO":
        sendKeyCombo(virtualKey: 6, flags: .maskCommand) // Cmd + Z
    case "FULLSCREEN":
        sendKeyCombo(virtualKey: 3, flags: [.maskCommand, .maskControl]) // Ctrl + Cmd + F
    case "APP_SWITCHER":
        sendKeyCombo(virtualKey: 48, flags: .maskCommand) // Cmd + Tab
    case "VOL_UP":
        runAppleScript("set volume output volume ((output volume of (get volume settings)) + 6)")
    case "VOL_DOWN":
        runAppleScript("set volume output volume ((output volume of (get volume settings)) - 6)")
    case "MUTE":
        runAppleScript("set volume output muted (not (output muted of (get volume settings)))")
    case "PLAY_PAUSE":
        runAppleScript("tell application \"System Events\" to key code 100")
    default:
        break
    }
}

func typeText(_ text: String) {
    let source = CGEventSource(stateID: .hidSystemState)
    for char in text.utf16 {
        var code = char
        let down = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true)
        down?.keyboardSetUnicodeString(stringLength: 1, unicodeString: &code)
        let up = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false)
        up?.keyboardSetUnicodeString(stringLength: 1, unicodeString: &code)
        down?.post(tap: .cghidEventTap)
        up?.post(tap: .cghidEventTap)
    }
}

func pressKey(virtualKey: CGKeyCode) {
    let source = CGEventSource(stateID: .hidSystemState)
    let down = CGEvent(keyboardEventSource: source, virtualKey: virtualKey, keyDown: true)
    let up = CGEvent(keyboardEventSource: source, virtualKey: virtualKey, keyDown: false)
    down?.post(tap: .cghidEventTap)
    up?.post(tap: .cghidEventTap)
}

// MARK: - MJPEG Video Streamer (HTTP on port 8081)
class MJPEGStreamer {
    private var listener: NWListener?
    private var connections = [NWConnection]()
    private var isCapturing = false
    private let streamLock = NSLock()
    let port: UInt16 = 8081

    func start() {
        guard let p = NWEndpoint.Port(rawValue: port) else { return }
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            listener = try NWListener(using: params, on: p)
            listener?.newConnectionHandler = { [weak self] conn in
                conn.start(queue: .global(qos: .userInteractive))
                self?.handleClient(conn)
            }
            listener?.start(queue: .global())
            print(" Video Streamer listening on HTTP: http://<macIp>:\(port)/stream")
        } catch {
            print("Failed to start video streamer: \(error.localizedDescription)")
        }
    }

    private func handleClient(_ conn: NWConnection) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 1024) { [weak self] _, _, _, _ in
            guard let self = self else { return }
            let header = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\nContent-Type: multipart/x-mixed-replace; boundary=--frame\r\n\r\n"
            conn.send(content: header.data(using: .utf8), completion: .contentProcessed({ _ in }))

            self.streamLock.lock()
            self.connections.append(conn)
            let needsStart = !self.isCapturing
            if needsStart {
                self.isCapturing = true
            }
            self.streamLock.unlock()

            if needsStart {
                self.startCaptureLoop()
            }
        }
    }

    private func startCaptureLoop() {
        Task {
            print("[Stream] Screen capture loop started")
            while true {
                self.streamLock.lock()
                self.connections.removeAll(where: {
                    if case .cancelled = $0.state { return true }
                    if case .failed = $0.state { return true }
                    return false
                })
                let count = self.connections.count
                if count == 0 {
                    self.isCapturing = false
                    self.streamLock.unlock()
                    print("[Stream] No viewers connected; pausing capture")
                    break
                }
                self.streamLock.unlock()

                do {
                    let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
                    if let display = content.displays.first {
                        let filter = SCContentFilter(display: display, excludingWindows: [])
                        let config = SCStreamConfiguration()
                        config.width = 960
                        config.height = 540
                        config.showsCursor = true

                        let image = try await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config)
                        let mutableData = NSMutableData()
                        if let dest = CGImageDestinationCreateWithData(mutableData as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil) {
                            let options: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.45]
                            CGImageDestinationAddImage(dest, image, options as CFDictionary)
                            CGImageDestinationFinalize(dest)
                        }

                        let jpegData = mutableData as Data
                        let header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: \(jpegData.count)\r\n\r\n".data(using: .utf8)!
                        let footer = "\r\n".data(using: .utf8)!
                        var packet = Data()
                        packet.append(header)
                        packet.append(jpegData)
                        packet.append(footer)

                        self.streamLock.lock()
                        for c in self.connections {
                            c.send(content: packet, completion: .contentProcessed({ _ in }))
                        }
                        self.streamLock.unlock()
                    }
                } catch {
                    // Screen recording permission may be required
                }

                try? await Task.sleep(nanoseconds: 50_000_000) // ~20 FPS
            }
        }
    }
}

// MARK: - UDP Trackpad Server
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

            print("========================================")
            print(" Trackpad Server listening on UDP: \(port)")
            print(" Available Mac IP Addresses:")
            let ips = getLocalIPAddresses()
            if ips.isEmpty {
                print("   (No network interfaces found)")
            } else {
                for ip in ips {
                    print("   ➜ \(ip)")
                }
            }
            print(" Enter one of the above IPs in the Android app")
            print("========================================")
            fflush(stdout)
        } catch {
            print("Failed to start listener: \(error)")
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
        if message == "DISCOVER_SERVER" || message == "CONNECT" || message == "PING" {
            print("[UDP] Handshake '\(message)' received from \(connection.endpoint)")
            let response = "CONNECTED".data(using: .utf8)
            connection.send(content: response, completion: .contentProcessed({ error in
                if let error = error {
                    print("[UDP] Failed to reply: \(error)")
                } else {
                    print("[UDP] Replied 'CONNECTED' to \(connection.endpoint)")
                }
            }))
            fflush(stdout)
            return
        }

        if message == "GET_APPS" {
            let apps = getInstalledApps().joined(separator: ",")
            let response = "APPS:\(apps)".data(using: .utf8)
            connection.send(content: response, completion: .contentProcessed({ _ in }))
            return
        }

        if message == "GET_TABS" {
            DispatchQueue.global(qos: .userInitiated).async {
                let tabsStr = refreshOpenWindowsAndTabs()
                let response = "TABS:\(tabsStr)".data(using: .utf8)
                connection.send(content: response, completion: .contentProcessed({ _ in }))
                print("[UDP] Sent \(cachedTabs.count) tabs to \(connection.endpoint)")
            }
            return
        }

        if message.hasPrefix("SWITCH_TAB,") {
            let idStr = String(message.dropFirst("SWITCH_TAB,".count))
            if let id = Int(idStr) {
                DispatchQueue.global(qos: .userInitiated).async {
                    doSwitchTab(id: id)
                }
            }
            return
        }

        if message.hasPrefix("LAUNCH_APP,") {
            let appName = String(message.dropFirst("LAUNCH_APP,".count))
            DispatchQueue.main.async {
                launchApp(name: appName)
            }
            return
        }

        if message.hasPrefix("ACTION,") {
            let action = String(message.dropFirst("ACTION,".count))
            DispatchQueue.main.async {
                handleMacAction(action)
            }
            return
        }

        if message.hasPrefix("TYPE_B64,") {
            let b64 = String(message.dropFirst("TYPE_B64,".count))
            if let data = Data(base64Encoded: b64), let text = String(data: data, encoding: .utf8) {
                DispatchQueue.main.async {
                    typeText(text)
                }
            }
            return
        }

        if message.hasPrefix("KEY,") {
            let key = String(message.dropFirst("KEY,".count)).uppercased()
            DispatchQueue.main.async {
                switch key {
                case "ENTER":
                    pressKey(virtualKey: 36)
                case "BACKSPACE":
                    pressKey(virtualKey: 51)
                case "SPACE":
                    pressKey(virtualKey: 49)
                case "TAB":
                    pressKey(virtualKey: 48)
                case "ESCAPE":
                    pressKey(virtualKey: 53)
                default:
                    break
                }
            }
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
    private let streamer = MJPEGStreamer()

    func applicationDidFinishLaunching(_ notification: Notification) {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        if !AXIsProcessTrustedWithOptions(options) {
            print("Accessibility permission required. Please enable in System Settings > Privacy & Security > Accessibility.")
        }

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem?.button?.title = "📱 Trackpad"

        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "Trackpad & Stream Server Active", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        statusItem?.menu = menu

        server.start()
        streamer.start()
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
