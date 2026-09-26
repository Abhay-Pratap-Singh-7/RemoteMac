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

func getStoredApiKey() -> String {
    let keyPath = "/Users/abhay/Downloads/HotspotTrackpad/macOS/gemini_key.txt"
    if let key = try? String(contentsOfFile: keyPath, encoding: .utf8) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
    }
    if let envKey = ProcessInfo.processInfo.environment["GEMINI_API_KEY"], !envKey.isEmpty {
        return envKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    return ""
}

func saveStoredApiKey(_ key: String) {
    let keyPath = "/Users/abhay/Downloads/HotspotTrackpad/macOS/gemini_key.txt"
    try? key.trimmingCharacters(in: .whitespacesAndNewlines).write(toFile: keyPath, atomically: true, encoding: .utf8)
    print("[AI] Gemini API Key saved to \(keyPath)")
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

func executeAICommand(type: String, command: String) {
    print("[AI Execution] Running [\(type)]: \(command)")
    let task = Process()
    switch type.lowercased() {
    case "shell":
        task.executableURL = URL(fileURLWithPath: "/bin/zsh")
        task.arguments = ["-c", command]
    case "applescript":
        task.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        task.arguments = ["-e", command]
    case "open_url":
        task.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        task.arguments = [command]
    case "launch_app":
        task.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        task.arguments = ["-a", command]
    default:
        task.executableURL = URL(fileURLWithPath: "/bin/zsh")
        task.arguments = ["-c", command]
    }
    do {
        try task.run()
        task.waitUntilExit()
        print("[AI Execution] Finished with code \(task.terminationStatus)")
    } catch {
        print("[AI Execution] Failed: \(error.localizedDescription)")
    }
}

func callGeminiAndExecute(prompt: String, completion: @escaping (Bool, String, String) -> Void) {
    let apiKey = getStoredApiKey()
    guard !apiKey.isEmpty else {
        completion(false, "Gemini API key not found on Mac. Please save your key in gemini_key.txt", "")
        return
    }

    let candidateUrls = [
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=\(apiKey)",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=\(apiKey)",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=\(apiKey)",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=\(apiKey)",
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=\(apiKey)"
    ]

    Task {
        var lastError = ""
        for urlStr in candidateUrls {
            guard let url = URL(string: urlStr) else { continue }
            let modelName = url.pathComponents.last ?? "model"
            print("[AI] Querying Gemini model: \(modelName)")
            fflush(stdout)

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")

            let payload: [String: Any] = [
                "contents": [
                    [
                        "parts": [
                            ["text": "You are a macOS automation agent. Convert this user task into an executable macOS command: \"\(prompt)\". Respond ONLY with valid JSON: {\"type\": \"shell\"|\"applescript\"|\"open_url\"|\"launch_app\", \"command\": \"...\", \"summary\": \"...\"}"]
                        ]
                    ]
                ],
                "generationConfig": [
                    "response_mime_type": "application/json",
                    "temperature": 0.1
                ]
            ]

            guard let jsonData = try? JSONSerialization.data(withJSONObject: payload) else { continue }
            request.httpBody = jsonData

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode) {
                    if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let candidates = root["candidates"] as? [[String: Any]],
                       let first = candidates.first,
                       let content = first["content"] as? [String: Any],
                       let parts = content["parts"] as? [[String: Any]],
                       let textPart = parts.first?["text"] as? String,
                       let textData = textPart.data(using: .utf8),
                       let cmdJson = try? JSONSerialization.jsonObject(with: textData) as? [String: Any],
                       let type = cmdJson["type"] as? String,
                       let command = cmdJson["command"] as? String {
                        let summary = (cmdJson["summary"] as? String) ?? "Executed task"
                        print("[AI Success] Model: \(modelName) -> [\(type)] \(command) (\(summary))")
                        fflush(stdout)
                        DispatchQueue.main.async {
                            executeAICommand(type: type, command: command)
                        }
                        completion(true, summary, command)
                        return
                    }
                } else if let httpResp = response as? HTTPURLResponse {
                    let errBody = String(data: data, encoding: .utf8) ?? ""
                    lastError = "HTTP \(httpResp.statusCode): \(errBody.prefix(120))"
                    print("[AI Warning] Model \(modelName) returned \(lastError)")
                    fflush(stdout)
                }
            } catch {
                lastError = error.localizedDescription
                print("[AI Warning] Model \(modelName) request failed: \(lastError)")
                fflush(stdout)
            }
        }
        print("[AI Error] All models failed. Last error: \(lastError)")
        fflush(stdout)
        completion(false, lastError.isEmpty ? "Failed to query Gemini model" : lastError, "")
    }
}

func handleMacAction(_ action: String) {
    switch action {
    case "MISSION_CONTROL":
        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        task.arguments = ["-a", "Mission Control"]
        try? task.run()
    case "SPOTLIGHT":
        sendKeyCombo(virtualKey: 49, flags: .maskCommand)
    case "DESKTOP":
        sendKeyCombo(virtualKey: 103, flags: [])
    case "CLOSE_WINDOW":
        sendKeyCombo(virtualKey: 13, flags: .maskCommand)
    case "QUIT_APP":
        sendKeyCombo(virtualKey: 12, flags: .maskCommand)
    case "COPY":
        sendKeyCombo(virtualKey: 8, flags: .maskCommand)
    case "PASTE":
        sendKeyCombo(virtualKey: 9, flags: .maskCommand)
    case "UNDO":
        sendKeyCombo(virtualKey: 6, flags: .maskCommand)
    case "FULLSCREEN":
        sendKeyCombo(virtualKey: 3, flags: [.maskCommand, .maskControl])
    case "APP_SWITCHER":
        sendKeyCombo(virtualKey: 48, flags: .maskCommand)
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

    var onFrameCaptured: ((Data) -> Void)?
    var hasRelayViewer: (() -> Bool)?

    func triggerCaptureIfNeeded() {
        streamLock.lock()
        let needsStart = !isCapturing
        if needsStart {
            isCapturing = true
        }
        streamLock.unlock()
        if needsStart {
            startCaptureLoop()
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
                let relayActive = self.hasRelayViewer?() ?? false
                if count == 0 && !relayActive {
                    self.isCapturing = false
                    self.streamLock.unlock()
                    print("[Stream] No viewers connected; pausing capture")
                    break
                }
                self.streamLock.unlock()

                do {
                    let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
                    guard let display = content.displays.first else {
                        print("[Stream Warning] No active display found (is lid folded without external display?)")
                        fflush(stdout)
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        continue
                    }
                    let filter = SCContentFilter(display: display, excludingWindows: [])
                    let config = SCStreamConfiguration()
                    let targetWidth = 1280
                    let targetHeight = Int(Double(targetWidth) * (Double(display.height) / Double(display.width)))
                    config.width = targetWidth
                    config.height = targetHeight
                    config.showsCursor = true

                    let image = try await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config)
                    let mutableData = NSMutableData()
                    if let dest = CGImageDestinationCreateWithData(mutableData as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil) {
                        let options: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.58]
                        CGImageDestinationAddImage(dest, image, options as CFDictionary)
                        CGImageDestinationFinalize(dest)
                    }

                    let jpegData = mutableData as Data
                    self.onFrameCaptured?(jpegData)
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
                } catch {
                    print("[Stream Capture Error] \(error.localizedDescription)")
                    fflush(stdout)
                    try? await Task.sleep(nanoseconds: 500_000_000)
                }

                try? await Task.sleep(nanoseconds: 33_333_333) // ~30 FPS ultra-low latency
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
            let key = getStoredApiKey()
            if key.isEmpty {
                print(" [AI] Gemini Key: Not set (Paste in gemini_key.txt or via Android app)")
            } else {
                print(" [AI] Gemini Key: Configured (Server-side)")
            }
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
            fflush(stdout)
        }
        processIncomingCommand(message) { reply in
            let response = reply.data(using: .utf8)
            connection.send(content: response, completion: .contentProcessed({ _ in }))
        }
    }
}

func moveCursor(dx: CGFloat, dy: CGFloat) {
    let loc = CGEvent(source: nil)?.location ?? .zero
    let target = CGPoint(x: loc.x + dx, y: loc.y + dy)
    let moveEvent = CGEvent(mouseEventSource: nil, mouseType: .mouseMoved, mouseCursorPosition: target, mouseButton: .left)
    moveEvent?.post(tap: .cghidEventTap)
}

func clickMouse(right: Bool) {
    let loc = CGEvent(source: nil)?.location ?? .zero
    let downType: CGEventType = right ? .rightMouseDown : .leftMouseDown
    let upType: CGEventType = right ? .rightMouseUp : .leftMouseUp
    let button: CGMouseButton = right ? .right : .left

    let down = CGEvent(mouseEventSource: nil, mouseType: downType, mouseCursorPosition: loc, mouseButton: button)
    let up = CGEvent(mouseEventSource: nil, mouseType: upType, mouseCursorPosition: loc, mouseButton: button)
    down?.post(tap: .cghidEventTap)
    up?.post(tap: .cghidEventTap)
}

func scroll(dx: Int32, dy: Int32) {
    let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: dy, wheel2: dx, wheel3: 0)
    event?.post(tap: .cghidEventTap)
}

func processIncomingCommand(_ message: String, replyHandler: @escaping (String) -> Void) {
    if message == "DISCOVER_SERVER" || message == "CONNECT" || message == "PING" {
        let hasKey = !getStoredApiKey().isEmpty
        replyHandler("CONNECTED,\(hasKey ? "KEY_SET" : "NO_KEY")")
        return
    }

    if message == "CHECK_AI_KEY" {
        let hasKey = !getStoredApiKey().isEmpty
        replyHandler("AI_KEY_STATUS,\(hasKey ? "CONFIGURED" : "MISSING")")
        return
    }

    if message.hasPrefix("SET_AI_KEY,") {
        let key = String(message.dropFirst("SET_AI_KEY,".count)).trimmingCharacters(in: .whitespacesAndNewlines)
        saveStoredApiKey(key)
        replyHandler("AI_KEY_STATUS,CONFIGURED")
        replyHandler("AI_RESULT,SUCCESS,API Key Saved on Mac,")
        return
    }

    if message.hasPrefix("AI_EXEC,") {
        let rest = String(message.dropFirst("AI_EXEC,".count))
        let parts = rest.components(separatedBy: ",")
        if parts.count >= 2, let data = Data(base64Encoded: parts[1]), let cmd = String(data: data, encoding: .utf8) {
            DispatchQueue.main.async {
                executeAICommand(type: parts[0], command: cmd)
            }
        }
        return
    }

    if message.hasPrefix("AI_LOG,") {
        let logMsg = String(message.dropFirst("AI_LOG,".count))
        print("[App Terminal Log] \(logMsg)")
        fflush(stdout)
        return
    }

    if message.hasPrefix("AI_TASK_B64,") {
        let b64 = String(message.dropFirst("AI_TASK_B64,".count))
        if let data = Data(base64Encoded: b64), let prompt = String(data: data, encoding: .utf8) {
            print("[AI Task] Received prompt: \"\(prompt)\"")
            callGeminiAndExecute(prompt: prompt) { success, summary, command in
                let status = success ? "SUCCESS" : "ERROR"
                let safeSummary = summary.replacingOccurrences(of: ",", with: ";")
                let safeCmd = command.replacingOccurrences(of: ",", with: ";")
                replyHandler("AI_RESULT,\(status),\(safeSummary),\(safeCmd)")
            }
        }
        return
    }

    if message == "GET_APPS" {
        let apps = getInstalledApps().joined(separator: ",")
        replyHandler("APPS:\(apps)")
        return
    }

    if message == "GET_TABS" {
        DispatchQueue.global(qos: .userInitiated).async {
            let tabsStr = refreshOpenWindowsAndTabs()
            replyHandler("TABS:\(tabsStr)")
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

// MARK: - Cloud WebSocket Relay Client
class RelayClient {
    private var webSocketTask: URLSessionWebSocketTask?
    private var isRunning = false
    private let urlString: String
    private let room: String
    private let onCommand: (String, @escaping (String) -> Void) -> Void
    var isConnected = false
    var isPeerConnected = false

    init(urlString: String, room: String, onCommand: @escaping (String, @escaping (String) -> Void) -> Void) {
        self.urlString = urlString
        self.room = room
        self.onCommand = onCommand
    }

    func start() {
        isRunning = true
        connect()
    }

    private func connect() {
        guard isRunning else { return }
        var cleanUrl = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanUrl.hasSuffix("/") { cleanUrl.removeLast() }
        if cleanUrl.hasPrefix("https://") {
            cleanUrl = "wss://" + cleanUrl.dropFirst("https://".count)
        } else if cleanUrl.hasPrefix("http://") {
            cleanUrl = "ws://" + cleanUrl.dropFirst("http://".count)
        } else if !cleanUrl.hasPrefix("ws://") && !cleanUrl.hasPrefix("wss://") {
            cleanUrl = "wss://" + cleanUrl
        }
        let full = "\(cleanUrl)/?role=mac&room=\(room)"
        guard let url = URL(string: full) else { return }

        print("[Relay] Connecting to Cloud Relay at \(full)...")
        fflush(stdout)
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()
        self.isConnected = true
        listen()
    }

    private func listen() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if trimmed == "RELAY_PEER_CONNECTED,phone" {
                        self.isPeerConnected = true
                        print("[Relay] 🚀 Android phone connected via Cloud Relay!")
                        fflush(stdout)
                    } else if trimmed == "RELAY_PEER_DISCONNECTED,phone" {
                        self.isPeerConnected = false
                        print("[Relay] Android phone disconnected from Cloud Relay.")
                        fflush(stdout)
                    } else {
                        self.onCommand(trimmed) { reply in
                            self.send(reply)
                        }
                    }
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.onCommand(text) { reply in
                            self.send(reply)
                        }
                    }
                @unknown default:
                    break
                }
                self.listen()
            case .failure(let error):
                print("[Relay] Disconnected: \(error.localizedDescription). Reconnecting in 5s...")
                fflush(stdout)
                self.isConnected = false
                self.isPeerConnected = false
                DispatchQueue.global().asyncAfter(deadline: .now() + 5) {
                    if self.isRunning { self.connect() }
                }
            }
        }
    }

    func send(_ text: String) {
        webSocketTask?.send(.string(text)) { error in
            if let error = error {
                print("[Relay] Send text error: \(error.localizedDescription)")
            }
        }
    }

    private var isSendingFrame = false
    private let sendLock = NSLock()

    func sendBinary(_ data: Data) {
        guard isPeerConnected else { return }
        sendLock.lock()
        if isSendingFrame {
            // Drop frame to ensure zero queueing / real-time latency!
            sendLock.unlock()
            return
        }
        isSendingFrame = true
        sendLock.unlock()

        webSocketTask?.send(.data(data)) { [weak self] _ in
            self?.sendLock.lock()
            self?.isSendingFrame = false
            self?.sendLock.unlock()
        }
    }
}

func getRelayConfig() -> (url: String, room: String)? {
    let configPath = "/Users/abhay/Downloads/HotspotTrackpad/macOS/relay_config.txt"
    if let content = try? String(contentsOfFile: configPath, encoding: .utf8) {
        for line in content.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && !trimmed.hasPrefix("#") {
                let parts = trimmed.components(separatedBy: ",")
                if parts.count >= 2 {
                    return (parts[0].trimmingCharacters(in: .whitespaces), parts[1].trimmingCharacters(in: .whitespaces))
                }
            }
        }
    }
    if let envUrl = ProcessInfo.processInfo.environment["RELAY_URL"], !envUrl.isEmpty {
        let envRoom = ProcessInfo.processInfo.environment["RELAY_ROOM"] ?? "123456"
        return (envUrl, envRoom)
    }
    return nil
}

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem?
    private let server = TrackpadServer()
    private let streamer = MJPEGStreamer()
    private var relayClient: RelayClient?

    func applicationDidFinishLaunching(_ notification: Notification) {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        if !AXIsProcessTrustedWithOptions(options) {
            print("Accessibility permission required. Please enable in System Settings > Privacy & Security > Accessibility.")
        }

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem?.button?.title = "📱 Trackpad"

        let menu = NSMenu()
        menu.addItem(NSMenuItem(title: "Trackpad, Stream & AI Server Active", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(NSApplication.terminate(_:)), keyEquivalent: ""))
        statusItem?.menu = menu

        server.start()
        streamer.start()

        if let relay = getRelayConfig() {
            print("[Relay] Cloud Relay configured: \(relay.url) (Room: \(relay.room))")
            let client = RelayClient(urlString: relay.url, room: relay.room) { [weak self] message, reply in
                if message == "START_STREAM" {
                    self?.streamer.triggerCaptureIfNeeded()
                }
                processIncomingCommand(message, replyHandler: reply)
            }
            self.relayClient = client
            self.streamer.hasRelayViewer = { [weak client] in
                client?.isPeerConnected ?? false
            }
            self.streamer.onFrameCaptured = { [weak client] frame in
                client?.sendBinary(frame)
            }
            client.start()
        }
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()

