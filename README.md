# Universal gRPC Navigation (IntelliJ Plugin)

**Navigate from gRPC stub calls directly to their implementation classes in IntelliJ IDEA.**  
Tired of getting stuck in generated code (`Stub`, `ImplBase`, etc.) instead of actual service implementations? This plugin solves that problem.

---

## ✨ Features
- Jump (`Cmd+B`) from **gRPC stub calls** (e.g., `userStub.getUserInfo(...)`)  
  → directly to the **implementation method** in your project (`UserGrpcService.getUserInfo`).
- Works seamlessly in **Kotlin** and **Java** projects.
- Handles:
    - `*GrpcService` classes (preferred match)
    - `*ImplBase` or `*CoroutineImplBase` (fallback)

---

## 🛠 Compatibility
- **IDE Versions:** IntelliJ IDEA **2024.2 (242)** → **2025.2 (252.*)**
- **Products Supported:**
    - IntelliJ IDEA Ultimate
    - IntelliJ IDEA Community
- **Tested With:**
    - Kotlin 1.9+
    - JDK 17+
    - Protobuf 3.25+

---

## 🚀 Installation

### 1. Marketplace (Recommended)
Available on [JetBrains Marketplace](https://plugins.jetbrains.com/).  
Simply search for **Universal gRPC Navigation** inside IntelliJ IDEA.

### 2. Manual Installation
1. Download the `.zip` from the [Releases](https://github.com/taeyun1215/grpc-universal-navigation-intellij-plugin/releases).
2. In IntelliJ: `Settings → Plugins → ⚙ → Install Plugin from Disk`.
3. Restart IDE.

---

## 🧑‍💻 Usage
1. Write gRPC client code like:
   ```kotlin
   val result = userStub.getUserInfo(
       BasicRequest.newBuilder().setUserId("123").build()
   )