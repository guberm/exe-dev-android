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
- Copy a terminal command that starts a tiny Python/systemd preview server on port 8000.
- Use the built-in SSH terminal to generate/register a mobile SSH key and run commands on a VM from Android.
- Log in without a computer via **Login via SSH on this phone**, which opens an interactive `ssh exe.dev` flow, lets the user complete email/code prompts, then generates and saves the API token. The phone terminal filters exe.dev's animated banner so it does not create an endless scroll of repeated logo frames.
- Error and setup dialogs include a **Copy** button for sending diagnostics or commands elsewhere.
- Show a **Logout** button only when logged in; logout removes the saved API token and the app-generated mobile SSH key.
- Includes a custom vector/adaptive launcher icon.

## Authentication

exe.dev does not expose a conventional username/password mobile login flow. Its official API uses SSH commands or HTTPS bearer tokens.

The Android app supports phone-only login:

1. Open **Login / Settings**.
2. Tap **Login via SSH on this phone**.
3. The app generates a mobile SSH key and opens an interactive `ssh exe.dev` session.
4. Complete exe.dev's email / verification-code prompts in the phone terminal.
5. Tap **Generate and save API token**. The app saves the returned `exe1.` / `exe0.` token automatically.

Fallback for an already trusted computer:

1. On a trusted computer where `ssh exe.dev` works, run:

   ```bash
   ssh exe.dev ssh-key generate-api-key "--cmds=whoami,ls,new,ssh-key add,ssh-key list" --exp=30d
   ```

2. Copy the returned token. It usually starts with `exe1.` or `exe0.`. Treat it like a password.
3. In the Android app, open **Login / Settings**, paste the token, keep the default endpoint `https://exe.dev/exec`, and tap **Save and test login**.

For least privilege, create short-lived tokens and restrict commands if you do not need full defaults. The app needs these exe.dev commands for the main workflow:

- `whoami` for login testing
- `ls` for VM listing
- `new` for VM creation
- `ssh-key add` and `ssh-key list` for built-in mobile SSH terminal setup

The app does not run `ssh <vm> ...` through the HTTPS API. exe.dev returns HTTP 422 for that because VM shell commands require a real SSH session. The port 8000 setup button copies a PowerShell-safe `ssh ... "echo <base64> | base64 -d | sudo sh"` terminal command instead; run it in a terminal where `ssh exe.dev` works, then tap **Open HTTPS preview**.

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
