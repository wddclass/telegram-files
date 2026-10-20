"use client";
import Files from "@/components/files";
import { Card, CardContent } from "@/components/ui/card";
import ThemeToggleButton from "@/components/theme-toggle-button";
import Link from "next/link";
import TelegramIcon from "@/components/telegram-icon";
import { useTranslations } from "next-intl";

export default function AccountPage() {
  const t = useTranslations("meta");
  return (
    <div className="container mx-auto px-4 py-6">
      <Card className="mb-6">
        <CardContent className="p-4">
          <div className="relative flex items-center justify-between gap-4">
            <Link href={"/"} className="inline-flex">
              <TelegramIcon className="h-6 w-6" />
            </Link>

            <h3 className="text-lg font-semibold">{t("title")}</h3>

            <ThemeToggleButton />
          </div>
        </CardContent>
      </Card>
      <Files accountId="-1" chatId="-1" />
    </div>
  );
}
