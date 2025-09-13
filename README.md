# Agora Chat Console

This repository contains a minimal HTML/JavaScript demo for Agora Chat.

## Usage

1. Open `index.html` in a web browser.
2. The token field is pre-filled with a demo value. Replace it if needed.
3. (Optional) Change the user ID or app key.
4. Click **Login** to connect to `msync-api-61.chat.agora.io`.
5. Use **Get Friend List** to fetch friends.
6. Send messages and view incoming messages in the list.

> Note: The demo disables `XMLHttpRequest` credentials to avoid CORS issues when opened from the local file system.
> The connection explicitly enables HTTPS and bypasses the SDK's DNS by providing the `msync-api-61` URLs directly.
