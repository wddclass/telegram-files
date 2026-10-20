<p align="center">
    <img src="./web/public/favicon.svg" align="center" width="30%">
</p>
<p align="center"><h1 align="center">Telegram Files</h1></p>
<p align="center">
	<em><code>A self-hosted Telegram file downloader for continuous, stable, and unattended downloads.</code></em>
</p>
<p align="center">
	<img src="https://img.shields.io/github/license/jarvis2f/telegram-files?style=default&logo=opensourceinitiative&logoColor=white&color=0080ff" alt="license">
	<img src="https://img.shields.io/github/last-commit/jarvis2f/telegram-files?style=default&logo=git&logoColor=white&color=0080ff" alt="last-commit">
	<img src="https://img.shields.io/github/v/release/jarvis2f/telegram-files?style=default&logo=git&logoColor=white&color=0080ff" alt="release">
    <a href="https://codecov.io/gh/jarvis2f/telegram-files" > 
        <img src="https://codecov.io/gh/jarvis2f/telegram-files/graph/badge.svg?token=Y4YN2W8ARV"/> 
    </a>
</p>
<br>

## 🔗 Table of Contents

- [📍 Overview](#-overview)
- [🧩 Screenshots](#-screenshots)
- [🚀 Getting Started](#-getting-started)
- [⌨️ Development](#️-development)
    - [☑️ Prerequisites](#-prerequisites)
    - [⚙️ Installation](#-installation)
- [📌 Project Roadmap](#-project-roadmap)
- [🔰 Contributing](#-contributing)
- [🎗 License](#-license)
- [🆗 FAQs](#-faqs)

---

## 📍 Overview

* Seamless file downloads from Telegram channels and groups
* Support for multiple Telegram accounts to manage and download files simultaneously
* Pause and resume downloads anytime, with automatic file transfer to designated destinations
* Instant preview of downloaded videos and images
* Fully responsive design with mobile-friendly access, Progressive Web App (PWA) support, and offline capabilities
* Easily fetch files from Telegram shared links

---

## 🧩 Screenshots

<div align="center">
    <img src="./misc/preview-files-pc.gif" width="70%">
    <img src="./misc/preview-files-mobile.gif" width="17.6%">
</div>

<details closed>
<summary>More Screenshots</summary>
<div align="center">
    <img src="./misc/screenshot-3.png" align="center" style="width: 300px; height: 500px;">
    <img src="./misc/screenshot-4.png" align="center" style="width: 300px; height: 500px;">
</div>

<div align="center">
    <img src="./misc/screenshot.png" align="center" width="40%">
    <img src="./misc/screenshot-2.png" align="center" width="40%">
</div>
</details>

## 🚀 Getting Started

Before getting started with telegram-files, you should apply a telegram api id and hash. You can apply for it on
the [Telegram API](https://my.telegram.org/apps) page.

**Using `docker`**
&nbsp; [<img align="center" src="https://img.shields.io/badge/Docker-2CA5E0.svg?style={badge_style}&logo=docker&logoColor=white" />](https://www.docker.com/)

```shell
docker run -d \
  --name telegram-files \
  --restart always \
  -e APP_ENV=${APP_ENV:-prod} \
  -e APP_ROOT=${APP_ROOT:-/app/data} \
  -e TELEGRAM_API_ID=${TELEGRAM_API_ID} \
  -e TELEGRAM_API_HASH=${TELEGRAM_API_HASH} \
  -p 6543:80 \
  -v ./data:/app/data \
  ghcr.io/jarvis2f/telegram-files:latest
```

**Using `docker-compose`**

Copy [docker-compose.yaml](docker-compose.yaml) and [.env.example](.env.example) to your project directory and run the following command:

```sh
docker-compose up -d
```

### Fork image

This fork publishes multi-arch images (`linux/amd64` and `linux/arm64`) to GHCR when `custom-0.3.1` is pushed, and when a `custom-*` tag is pushed. Pushes to `custom-0.3.1` are tagged `custom-latest` and `sha-<short>`. A git tag such as `custom-0.3.1` is published under that tag name.

```yaml
services:
  telegram-files:
    image: ghcr.io/wddclass/telegram-files:custom-latest
    # or pin a build: ghcr.io/wddclass/telegram-files:sha-abcdef0
    restart: always
    ports:
      - "6543:80"
    environment:
      APP_ENV: "prod"
      APP_ROOT: "/app/data"
      TELEGRAM_API_ID: ${TELEGRAM_API_ID}
      TELEGRAM_API_HASH: ${TELEGRAM_API_HASH}
    volumes:
      - ./data:/app/data
```

The image name uses the repository owner in lowercase (`ghcr.io/<owner>/<repo>`). For this fork that is `ghcr.io/wddclass/telegram-files`.

### Rclone upload

Rclone upload is optional and **off by default**. Turn it on in Settings → Rclone upload. Downloaded files can be copied or moved to a remote such as Google Drive without blocking the download pipeline. Files already marked uploaded are not sent again. A failed upload can be retried from the file row.

| Setting | Meaning |
| --- | --- |
| Remote path | rclone destination, for example `googledrive:WC/CC`. |
| Path template | Relative path under the remote. Default `{chatId}/{messageId}/{fileName}`, which is the previous layout. |
| Date timezone | IANA zone for `{date:...}`. Empty uses the container `TZ` variable, then `Asia/Shanghai`. The date is the file's download-completion time. |
| Config file | Path to `rclone.conf` inside the container. Default `/app/data/rclone/rclone.conf`. |
| Extra flags | Optional rclone flags, for example `--transfers 4`. |
| Copy / Move | Copy leaves the local file. Move deletes it after a successful upload. |
| Immediately | Queue an upload when each download completes. |
| Scheduled | Interval in seconds (`3600`) or a 5-field cron expression (`0 */6 * * *`). Each run uploads completed files that match the type filter and are not uploaded yet. |
| File types | Defaults to videos. |

`rclone` is installed in the image. Put the config on the data volume (already mounted) or mount it yourself:

```yaml
services:
  telegram-files:
    image: ghcr.io/wddclass/telegram-files:custom-latest
    volumes:
      - ./data:/app/data
      # optional explicit config mount; ./data/rclone/rclone.conf is used otherwise
      - ./rclone.conf:/app/data/rclone/rclone.conf:ro
```

Create the remote on a machine that already has rclone (`rclone config`), then copy the resulting `rclone.conf` to `./data/rclone/rclone.conf`. The container user must be able to read that file.

Path placeholders:

| Placeholder | Value |
| --- | --- |
| `{date:FORMAT}` | Completion time formatted with a Java `DateTimeFormatter` pattern, for example `{date:yyyy-MM-dd}` or `{date:yyyyMMdd}`. A `/` in the pattern creates another folder. |
| `{accountName}` | Account display name (first name and last name). |
| `{accountUsername}` | Telegram username without `@`. If the account has none, the account id is used. |
| `{accountId}` | Telegram user id of the account that downloaded the file. |
| `{chatTitle}` | Chat title. If it is unknown, `chat-<chatId>`. |
| `{chatId}` `{messageId}` `{type}` `{fileName}` | Chat id, message id, file type, and the local file name. |

`{accountName}` and `{chatTitle}` keep Unicode, including styled letters such as `𝘄𝗱𝗱𝘇𝘇𝘇`. They are not converted to ASCII. `/ \ : * ? " < > |` and control characters become `_`. Usernames keep letters, digits, and `_`.

To reproduce a date folder named after the account, set the remote to `googledrive:WC/CC` and the template to `{accountName}-{date:yyyyMMdd}/{fileName}`. A file finished on 19 October 2026 in `Asia/Shanghai` for an account named `𝘄𝗱𝗱𝘇𝘇𝘇` is uploaded to `googledrive:WC/CC/𝘄𝗱𝗱𝘇𝘇𝘇-20261019/clip.mp4`. The settings page previews the path and uses the selected account's real name when one is signed in. Chat title and file name in that preview are samples.

rclone writes that exact object name (`copyto` or `moveto`). If a remote file with the same name already exists and is identical, rclone skips it. If the contents differ, rclone replaces the remote file. Add `{messageId}` to the template when file names can repeat on the same day.

### Telegram Bot 控制（可选）

不配置 `TG_BOT_TOKEN` 时，机器人不会启动，下载和网页不受影响。配置后，程序通过 Bot API **长轮询**收消息，不需要公网 Webhook。

1. 在 Telegram 里找 [@BotFather](https://t.me/BotFather)，发送 `/newbot`，按提示创建机器人，复制 token。
2. 查自己的数字用户 ID（不是 @用户名）。可以给 [@userinfobot](https://t.me/userinfobot) 或 [@getidsbot](https://t.me/getidsbot) 发一条消息。机器人日志里也会打出被拒绝的 `user id`，方便你把它加进白名单。
3. 把下面的变量写进 `.env` 或 `docker-compose.yaml` 的 `environment`，然后重启容器。先给机器人发 `/start`。

| 变量 | 作用 |
| --- | --- |
| `TG_BOT_TOKEN` | BotFather 给的 token。留空则关闭机器人。 |
| `TG_BOT_ALLOWED_USERS` | 逗号分隔的数字用户 ID 和/或 `@用户名`。**必填**。为空时机器人拒绝所有人，并在日志里警告，不会回复。 |
| `TG_BOT_PROXY` | 可选。Bot API 的 HTTP/SOCKS5 代理，例如 `http://127.0.0.1:7890` 或 `socks5://user:pass@10.0.0.1:1080`。账号里配置的 MTProto 代理不能给 Bot API 用；HTTP/SOCKS 可以填同一个地址。 |
| `TG_BOT_DEFAULT_ACCOUNT` | 可选。多个已登录账号时默认用哪一个，填网页里看到的数字账号 ID，或账号显示名。 |
| `TG_BOT_NOTIFY` | 可选。`true` 时，向私聊过机器人的白名单用户推送下载完成/失败、上传完成/失败。默认 `false`。 |
| `TG_BOT_LANG` | 可选。默认中文。设为 `en` 时命令回复改为英文。 |

白名单以外的人（包括群成员）发来的消息和按钮会被直接丢掉，不会收到任何说明。请在**私聊**里使用。群里的请求不会在群内回复，避免把文件列表暴露给其他人；在群里收普通消息还需要在 BotFather 里对这个机器人执行 `/setprivacy` 并选 Disable，否则它只能看到命令。

命令：

| 命令 | 作用 |
| --- | --- |
| `/status` | 已连接的 Telegram 账号和授权状态 |
| `/downloads` | 正在下载的文件（进度百分比、速度、大小），以及队列、暂停、失败数量 |
| `/uploads` | rclone 正在上传的文件、等待/失败数量、最近完成的上传。rclone 本身不提供实时百分比时会标明进度不可用 |
| `/help` | 帮助 |

把消息链接发给机器人即可下载，例如 `https://t.me/username/123` 或私有频道 `https://t.me/c/1234567890/123`。相册和一条消息里的多个媒体会用现有的链接解析（含相册补全）展开成列表，再用按钮选择：全部下载、仅视频、仅图片，或单个文件。下载进入现有任务队列，因此已开启的 rclone 自动上传同样生效。有多个已授权账号、且没有匹配的默认账号时，会先让你选账号。

```yaml
services:
  telegram-files:
    image: ghcr.io/wddclass/telegram-files:custom-latest
    restart: always
    ports:
      - "6543:80"
    environment:
      APP_ENV: "prod"
      APP_ROOT: "/app/data"
      TELEGRAM_API_ID: ${TELEGRAM_API_ID}
      TELEGRAM_API_HASH: ${TELEGRAM_API_HASH}
      # 留空整段即可关闭机器人
      TG_BOT_TOKEN: ${TG_BOT_TOKEN}
      TG_BOT_ALLOWED_USERS: ${TG_BOT_ALLOWED_USERS}
      # TG_BOT_PROXY: http://127.0.0.1:7890
      # TG_BOT_DEFAULT_ACCOUNT: "123456789"
      # TG_BOT_NOTIFY: "true"
      # TG_BOT_LANG: zh
    volumes:
      - ./data:/app/data
```

**Install on unRaid**

On unRaid, install from the Community Repositories by searching for `telegram-files`.

> **Important Note:** You should NOT expose the service to the public internet. Because the service is not secure.

---

## ⌨️ Development

### ☑️ Prerequisites

Before getting started with telegram-files, ensure your runtime environment meets the following requirements:

- **Programming Language:** JDK23,TypeScript
- **Package Manager:** Gradle,Npm
- **Container Runtime:** Docker

### ⚙️ Installation

Install telegram-files using one of the following methods:

**Build from source:**

1. Clone the telegram-files repository:

```sh
git clone https://github.com/jarvis2f/telegram-files
```

2. Navigate to the project directory:

```sh
cd telegram-files
```

3. Install the project dependencies:

**Using `npm`**
&nbsp; [<img align="center" src="https://img.shields.io/badge/npm-CB3837.svg?style={badge_style}&logo=npm&logoColor=white" />](https://www.npmjs.com/)

```sh
cd web
npm install
```

**Using `gradle`**
&nbsp; [<img align="center" src="https://img.shields.io/badge/Gradle-02303A.svg?style={badge_style}&logo=gradle&logoColor=white" />](https://gradle.org/)

```sh
cd api
gradle build
```

**Using `docker`**
&nbsp; [<img align="center" src="https://img.shields.io/badge/Docker-2CA5E0.svg?style={badge_style}&logo=docker&logoColor=white" />](https://www.docker.com/)

```sh
docker build -t jarvis2f/telegram-files .
```

## 📌 Project Roadmap

- ✅ **`Task 1`**: Automatically download files based on set rules.
- ✅ **`Task 2`**: Download statistics and reports.
- ✅ **`Task 3`**: Improve Telegram’s login functionality.
- ✅ **`Task 4`**: Support auto transfer files to other destinations.
- ✅ **`Task 5`**: File table is optimized using virtual lists.
- ✅ **`Task 6`**: Preload file information to support responsible searches.

---

## 🔰 Contributing

- **💬 [Join the Discussions](https://github.com/jarvis2f/telegram-files/discussions)**: Share your insights, provide
  feedback, or ask questions.
- **🐛 [Report Issues](https://github.com/jarvis2f/telegram-files/issues)**: Submit bugs found or log feature requests
  for the `telegram-files` project.
- **💡 [Submit Pull Requests](https://github.com/jarvis2f/telegram-files/blob/main/CONTRIBUTING.md)**: Review open PRs,
  and submit your own PRs.

<details closed>
<summary>Contributing Guidelines</summary>

1. **Fork the Repository**: Start by forking the project repository to your github account.
2. **Clone Locally**: Clone the forked repository to your local machine using a git client.
   ```sh
   git clone https://github.com/jarvis2f/telegram-files
   ```
3. **Create a New Branch**: Always work on a new branch, giving it a descriptive name.
   ```sh
   git checkout -b new-feature-x
   ```
4. **Make Your Changes**: Develop and test your changes locally.
5. **Commit Your Changes**: Commit with a clear message describing your updates.
   ```sh
   git commit -m 'Implemented new feature x.'
   ```
6. **Push to github**: Push the changes to your forked repository.
   ```sh
   git push origin new-feature-x
   ```
7. **Submit a Pull Request**: Create a PR against the original project repository. Clearly describe the changes and
   their motivations.
8. **Review**: Once your PR is reviewed and approved, it will be merged into the main branch. Congratulations on your
   contribution!

</details>

---

## 🎗 License

This project is protected under the MIT License. For more details,
refer to the [LICENSE](LICENSE) file.

---

## 🆗 FAQs

~~**Q.** Can't start the api server, error：`java.lang.UnsatisfiedLinkError: no tdjni in java.library.path`~~

~~**A.** Maybe download tdlib failed, you can see the [entrypoint.sh](entrypoint.sh) file, then download tdlib
manually.~~

**Q.** Web's spoiler is static, how to solve it?

**A.** 1. Because `CSS Houdini Paint API` is not supported by all browsers. 2. It is only supported on https.
<details closed>
<summary>Use in http environment, you can use the following method to solve it</summary>

Open the `chrome://flags` page, search for `Insecure origins treated as secure`, and add the address of the web page to
the list.
</details>

**Q.** How to use the telegram-files maintenance tool?

**A.** You can use the following command to run the maintenance tool(**before running the command, you should stop telegram-files container**):
<details closed>
<summary>Command</summary>

```shell
docker run --rm \
  --entrypoint tfm \
  -v $(pwd)/data:/app/data \
  -e APP_ROOT=${APP_ROOT:-/app/data} \
  -e TELEGRAM_API_ID=${TELEGRAM_API_ID} \
  -e TELEGRAM_API_HASH=${TELEGRAM_API_HASH} \
  ghcr.io/jarvis2f/telegram-files:latest ${Maintenance Command}
```

**Maintenance Command:**

- `album-caption`: Fixed issue with missing caption for album messages before `0.1.15`.
- `thumbnail`: Fixed issue with missing clear thumbnail.
</details>
