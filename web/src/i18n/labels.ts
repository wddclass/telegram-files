"use client";

import { useTranslations } from "next-intl";

const FILE_TYPES = ["all", "media", "photo", "video", "audio", "file"] as const;
export type FileTypeKey = (typeof FILE_TYPES)[number];

export function isFileTypeKey(value: string): value is FileTypeKey {
  return (FILE_TYPES as readonly string[]).includes(value);
}

const CHAT_TYPES = [
  "private",
  "group",
  "basicGroup",
  "supergroup",
  "channel",
  "secret",
] as const;
export type ChatTypeKey = (typeof CHAT_TYPES)[number];

export function chatTypeKey(value: string): ChatTypeKey | undefined {
  const normalized = value.trim();
  if ((CHAT_TYPES as readonly string[]).includes(normalized)) {
    return normalized as ChatTypeKey;
  }
  const lower = normalized.toLowerCase();
  if (lower === "basicgroup" || lower === "basic_group") return "basicGroup";
  if (lower === "supergroup" || lower === "super_group") return "supergroup";
  if ((CHAT_TYPES as readonly string[]).includes(lower)) {
    return lower as ChatTypeKey;
  }
  return undefined;
}

const TELEGRAM_STATES = [
  "connecting",
  "connectingToProxy",
  "ready",
  "updating",
  "waitingForNetwork",
] as const;

export function useTelegramStateLabel() {
  const t = useTranslations("connection.states");
  return (state: string) => {
    const match = TELEGRAM_STATES.find(
      (item) => item.toLowerCase() === state.toLowerCase(),
    );
    return match ? t(match) : state;
  };
}

const WS_STATUS = {
  Connecting: "connecting",
  Open: "open",
  Closing: "closing",
  Closed: "closed",
  Uninstantiated: "uninstantiated",
} as const;

export function useConnectionLabel() {
  const t = useTranslations("connection");
  return (status: string) => {
    const key = WS_STATUS[status as keyof typeof WS_STATUS];
    return key ? t(key) : status;
  };
}

export function translatePreviewError(
  message: string,
  t: ReturnType<typeof useTranslations<"settings.errors">>,
): string {
  const timezone = /^invalid timezone:\s*(.*)$/i.exec(message);
  if (timezone) return t("invalidTimezone", { value: timezone[1] ?? "" });
  if (/path template is too long/i.test(message)) return t("templateTooLong");
  const pattern = /^invalid date pattern:\s*(.*)$/i.exec(message);
  if (pattern) return t("invalidDatePattern", { value: pattern[1] ?? "" });
  if (/unsafe segment/i.test(message)) return t("unsafeSegment");
  if (/path template is empty/i.test(message)) return t("templateEmpty");
  return message;
}
