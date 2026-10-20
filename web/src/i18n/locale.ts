"use client";

import { useLocale } from "next-intl";
import { enUS, zhCN } from "date-fns/locale";
import type { Locale } from "date-fns";

export function useDateFnsLocale(): Locale {
  const locale = useLocale();
  return locale.toLowerCase().startsWith("zh") ? zhCN : enUS;
}

export function useIntlLocaleTag(): string {
  const locale = useLocale();
  return locale.toLowerCase().startsWith("zh") ? "zh-CN" : "en";
}
