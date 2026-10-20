"use client";

import React, { createContext, useContext, useEffect } from "react";
import { NextIntlClientProvider, useTranslations } from "next-intl";
import { useLocalStorage } from "@/hooks/use-local-storage";
import { en } from "@/i18n/messages/en";
import { zhCN } from "@/i18n/messages/zh-CN";

export const LOCALES = ["zh-CN", "en"] as const;
export type AppLocale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: AppLocale = "zh-CN";
export const LOCALE_STORAGE_KEY = "ui.locale";

const messages: Record<AppLocale, typeof zhCN> = {
  "zh-CN": zhCN,
  en,
};

export function isLocale(value: unknown): value is AppLocale {
  return value === "zh-CN" || value === "en";
}

const LocaleActions = createContext<(locale: AppLocale) => void>(() => undefined);

export function useSetLocale() {
  return useContext(LocaleActions);
}

function DocumentMeta({ locale }: { locale: AppLocale }) {
  const t = useTranslations("meta");
  useEffect(() => {
    document.documentElement.lang = locale;
    document.title = t("title");
    const description = document.querySelector('meta[name="description"]');
    if (description) {
      description.setAttribute("content", t("description"));
    }
  }, [locale, t]);
  return null;
}

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [stored, setStored] = useLocalStorage<AppLocale>(
    LOCALE_STORAGE_KEY,
    DEFAULT_LOCALE,
  );
  const locale = isLocale(stored) ? stored : DEFAULT_LOCALE;

  return (
    <NextIntlClientProvider
      locale={locale}
      messages={messages[locale]}
      timeZone={locale === "zh-CN" ? "Asia/Shanghai" : "UTC"}
    >
      <LocaleActions.Provider value={setStored}>
        <DocumentMeta locale={locale} />
        {children}
      </LocaleActions.Provider>
    </NextIntlClientProvider>
  );
}
