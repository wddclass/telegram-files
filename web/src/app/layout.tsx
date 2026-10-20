import "@/styles/globals.css";
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import React from "react";
import { Toaster } from "@/components/ui/toaster";
import { SWRProvider } from "@/components/swr-provider";
import { SettingsProvider } from "@/hooks/use-settings";
import { WebSocketProvider } from "@/hooks/use-websocket";
import { env } from "@/env";
import { TelegramAccountProvider } from "@/hooks/use-telegram-account";
import { ThemeProvider } from "@/components/theme-provider";
import { LocalStorageProvider } from "@/hooks/use-local-storage";
import { I18nProvider } from "@/i18n/provider";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Telegram 文件",
  description: "管理 Telegram 上的文件",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <link
          rel="icon"
          type="image/png"
          href="/favicon-96x96.png"
          sizes="96x96"
        />
        <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
        <link rel="shortcut icon" href="/favicon.ico" />
        <link
          rel="apple-touch-icon"
          sizes="180x180"
          href="/apple-touch-icon.png"
        />
        <meta name="apple-mobile-web-app-title" content="TeleFiles" />
        <link rel="manifest" href="/site.webmanifest" />
        {env.NEXT_PUBLIC_SCAN && (
          <script
            src="https://unpkg.com/react-scan/dist/auto.global.js"
            async
          />
        )}
      </head>
      <body className={inter.className}>
        <LocalStorageProvider>
          <I18nProvider>
          <ThemeProvider
            attribute="class"
            defaultTheme="light"
            disableTransitionOnChange
          >
            <SWRProvider>
              <WebSocketProvider>
                <SettingsProvider>
                  <TelegramAccountProvider>{children}</TelegramAccountProvider>
                </SettingsProvider>
              </WebSocketProvider>
            </SWRProvider>
            <Toaster />
          </ThemeProvider>
          </I18nProvider>
        </LocalStorageProvider>
      </body>
    </html>
  );
}
