# GS Agent

Professional on-device AI agent for Android — file management, shell execution, project editing, and multi-step autonomous task completion. Inspired by Antigravity-style agent UX.

## Features

- 🤖 **Multi-provider AI** — OpenAI, Anthropic Claude, Google Gemini, OpenRouter, Groq, xAI Grok, DeepSeek, Mistral, Together AI, Fireworks, Cohere, Perplexity, Ollama, LM Studio, and Custom OpenAI-compatible endpoints.
- 🛠️ **Custom model IDs** — Add any model ID manually per provider with full base URL override.
- 📁 **File system tools** — read / write / list / delete / move / search / edit (powered by `MANAGE_EXTERNAL_STORAGE`).
- 💻 **Shell execution** — `run_shell` tool for arbitrary commands (subject to Android sandbox).
- 🧠 **Agent loop** — model emits ` ```tool ` blocks, app executes the tool, feeds result back, repeats up to 12 steps.
- 📺 **Live console** — real-time tool calls + output, toggle button to collapse/expand. When collapsed, only task name + tool chips remain visible.
- 🎨 **Modern Material 3 UI** — dark theme, purple/cyan gradient accents, edge-to-edge layout.
- 💾 **Local persistence** — Room database for conversations, DataStore for settings, API keys stay on-device.

## Build

```bash
./gradlew assembleDebug
```

Requirements: Android SDK 34, JDK 17, Gradle 8.7 (handled by wrapper).

Minimum Android: 11 (API 30). Target: Android 14 (API 34).

## Architecture

- `data/providers/AiClient.kt` — unified streaming client (OpenAI-compatible, Anthropic, Gemini)
- `agent/tools/*` — pluggable Tool registry (file ops, shell, device info, etc.)
- `agent/executor/AgentExecutor.kt` — agent loop with tool-call extraction
- `ui/screens/*` — Compose UI (Permissions, Conversations, Chat, Settings)
- `data/db/AppDatabase.kt` — Room persistence

## Permissions

- `INTERNET` — talk to AI providers
- `MANAGE_EXTERNAL_STORAGE` — full file access for the agent
- `POST_NOTIFICATIONS` — task progress

API keys are stored locally only.
