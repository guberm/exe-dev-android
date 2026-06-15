# exe.dev Android

A small native Android client for [exe.dev](https://exe.dev/) with an English UI.

## Features

- Log in with an exe.dev HTTPS API bearer token.
- Test the account with `whoami`.
- List existing VMs via the official `POST https://exe.dev/exec` API.
- Create a new VM using the documented `new` command.
- Copy SSH connection commands for existing VMs.
- Open VM HTTPS URLs when the API returns one.
- Explain exe.dev's default port 8000 preview behavior.
- Open a VM into a phone-native SSH terminal with command input, Send, Ctrl-C, output copy, reconnect, and a one-tap port 8000 preview setup command.
- Log in without a computer via **Login via SSH on this phone**. The app auto-connects, lets the user complete email/code prompts only if exe.dev asks, then automatically generates, saves, and copies the `exe1.` / `exe0.` bearer token when the exe.dev prompt is ready. It also registers the phone SSH key automatically, so manual copy/paste is only a fallback.
- Error and setup dialogs include a **Copy** button for sending diagnostics or commands elsewhere.
- Show a **Logout** button only when logged in; logout removes the saved API token and the app-generated mobile SSH key.
- Includes a custom vector/adaptive launcher icon.

## Authentication

exe.dev does not expose a conventional username/password mobile login flow. Its official API uses SSH commands or HTTPS bearer tokens.

The Android app supports phone-only login:

1. Open **Login / Settings**.
2. Tap **Login via SSH on this phone**.
3. The app generates a mobile SSH key and auto-connects to `ssh exe.dev`.
4. If exe.dev asks for email / verification-code prompts, complete them in the phone terminal.
5. Once the `exe.dev ▶` prompt appears, the app automatically sends a unique `ssh-key generate-api-key ...` command, saves the returned `exe1.` / `exe0.` token, copies it to the Android clipboard, and registers the phone SSH public key. No manual token paste is required.

Fallback for an already trusted computer:

1. On a trusted computer where `ssh exe.dev` works, run:

   ```bash
   ssh exe.dev ssh-key generate-api-key --label=android-phone-$(date +%s) "--cmds=whoami,ls,new,ssh-key add,ssh-key list" --exp=30d
   ```

2. Copy the returned token. It usually starts with `exe1.` or `exe0.`. Treat it like a password.
3. In the Android app, open **Login / Settings**, paste the token, keep the default endpoint `https://exe.dev/exec`, and tap **Save and test login**.

For least privilege, create short-lived tokens and restrict commands if you do not need full defaults. The app needs these exe.dev commands for the main workflow:

- `whoami` for login testing
- `ls` for VM listing
- `new` for VM creation
- `ssh-key add` and `ssh-key list` for built-in mobile SSH terminal setup

The app does not run `ssh <vm> ...` through the HTTPS API. exe.dev returns HTTP 422 for that because VM shell commands require a real SSH session. Use **Open terminal** on a VM to get a direct phone SSH shell; the app starts the port 8000 preview server automatically after connect.

## Build

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug --console=plain --no-daemon
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Tokens are stored in Android `SharedPreferences` on the device.
- The app does not include, generate, or commit any exe.dev token.
- The built-in SSH terminal stores a generated private key in the app's private storage and registers only its public key with exe.dev.
