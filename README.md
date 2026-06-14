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
- Start nginx on a VM as a quick way to bind port 8000 for the default HTTPS preview page.
- Includes a custom vector/adaptive launcher icon.

## Authentication

exe.dev does not expose a conventional username/password mobile login flow. Its official API uses SSH commands or HTTPS bearer tokens.

The Android app includes a 3-step login wizard:

1. On a trusted computer where `ssh exe.dev` works, run:

   ```bash
   ssh exe.dev ssh-key generate-api-key --exp=30d
   ```

2. Copy the returned token. It usually starts with `exe1.` or `exe0.`. Treat it like a password.
3. In the Android app, open **Login / Settings**, paste the token, keep the default endpoint `https://exe.dev/exec`, and tap **Save and test login**.

For least privilege, create short-lived tokens and restrict commands if you do not need full defaults. The app needs these exe.dev commands for the main workflow:

- `whoami` for login testing
- `ls` for VM listing
- `new` for VM creation

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
- Interactive SSH is intentionally delegated to Android SSH clients by copying the SSH command; Android does not include a built-in SSH terminal.
