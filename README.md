# Edited History — Android phone app

Real Android app UI inspired by a mobile chat interface. Uses direct HTTPS requests from the phone to OpenAI, Google Gemini, Anthropic Claude, and OpenRouter. No running PC or separate backend is required after installation. The APK needs an internet connection and your own API key. This is **not the official ChatGPT app**.

## Build from your phone (GitHub cloud build)

1. On your Android phone, create a new **private** GitHub repository (GitHub website).
2. Upload **the contents** of this project zip to the repository, preserving the `.github/workflows/build.yml` folder structure. The GitHub website may require uploading files/folders from an extracted zip using its file-upload UI; a Git client app can also help.
3. In the GitHub repository, go to **Actions → Build Android APK → Run workflow**. If necessary enable Actions in the repo settings.
4. When the workflow finishes, tap its uploaded artifact `EditedHistory-debug-apk`, download/unzip it, then install `app-debug.apk` on your phone. Android may ask you to allow installing APKs from your browser/file manager. Only install APKs from builds you trust.
5. Open app → settings (⋮) → select provider → paste key → Save key on device → Done → chat.

Alternatively, in Android Studio (PC), open project and run `gradle assembleDebug` or build the debug APK using the IDE.

## How editing works
Tap **Edit** under an assistant message; when Gaslight Mode is ON, the changed text is sent as an assistant message in the next provider API request. When OFF, edited messages are omitted from the request. The model can still recognize inconsistencies and disagree.

## Security and limitations
- Keys are encrypted at rest with Android Keystore and stored in private app preferences; they're sent directly to the selected API provider in HTTPS requests. Your text is sent to the provider.
- API keys supplied to a client-side app are never as abuse-resistant as server-side managed credentials, particularly on compromised devices. Use restricted keys/spending limits where supported.
- Chat history is stored in WebView local storage (not encrypted separately).
- Non-streaming text replies; API calls may be billed. Requires working internet.
- Model identifiers can change; set the model in Settings if defaults stop working.
- Not compiled or device-tested in this environment. The included GitHub Actions workflow builds the APK remotely.
