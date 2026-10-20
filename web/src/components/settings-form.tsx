import { Bell, Copy } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import React, { type FormEvent, useEffect, useState } from "react";
import { useSettings } from "@/hooks/use-settings";
import { useTelegramAccount } from "@/hooks/use-telegram-account";
import { useCopyToClipboard } from "@/hooks/use-copy-to-clipboard";
import { DialogClose, DialogFooter } from "@/components/ui/dialog";
import TimeRangeSelector from "@/components/ui/time-range-selector";
import { Switch } from "@/components/ui/switch";
import { type SettingKey } from "@/lib/types";
import { request } from "@/lib/api";
import { useTranslations } from "next-intl";
import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  isLocale,
  useSetLocale,
  type AppLocale,
} from "@/i18n/provider";
import { useLocalStorage } from "@/hooks/use-local-storage";
import { translatePreviewError } from "@/i18n/labels";

type RcloneSettings = {
  enabled: boolean;
  remote: string;
  configPath: string;
  extraFlags: string;
  operation: "copy" | "move";
  mode: "immediate" | "scheduled";
  schedule: string;
  fileTypes: string;
  pathTemplate: string;
  timeZone: string;
};

const DEFAULT_RCLONE: RcloneSettings = {
  enabled: false,
  remote: "",
  configPath: "/app/data/rclone/rclone.conf",
  extraFlags: "",
  operation: "copy",
  mode: "immediate",
  schedule: "3600",
  fileTypes: "video",
  pathTemplate: "{chatId}/{messageId}/{fileName}",
  timeZone: "",
};

const RCLONE_FILE_TYPES = ["video", "photo", "audio", "file"] as const;

function parseRclone(value?: string): RcloneSettings {
  if (!value) return { ...DEFAULT_RCLONE };
  try {
    const parsed = JSON.parse(value) as Partial<RcloneSettings>;
    return { ...DEFAULT_RCLONE, ...parsed };
  } catch {
    return { ...DEFAULT_RCLONE };
  }
}
import { Slider } from "@/components/ui/slider";
import { TagsInput } from "@/components/ui/tags-input";
import { split } from "lodash";
import { RadioGroup, RadioGroupItem } from "./ui/radio-group";

export default function SettingsForm() {
  const { settings, setSetting, updateSettings } = useSettings();
  const { account } = useTelegramAccount();
  const [, copyToClipboard] = useCopyToClipboard();
  const t = useTranslations("settings");
  const tCommon = useTranslations("common");
  const tType = useTranslations("fileType");
  const tErrors = useTranslations("settings.errors");
  const setLocale = useSetLocale();
  const [storedLocale] = useLocalStorage<AppLocale>(
    LOCALE_STORAGE_KEY,
    DEFAULT_LOCALE,
  );
  const locale = isLocale(storedLocale) ? storedLocale : DEFAULT_LOCALE;

  const avgSpeedIntervalOptions = [
    { value: "60", label: t("intervals.60") },
    { value: "300", label: t("intervals.300") },
    { value: "600", label: t("intervals.600") },
    { value: "900", label: t("intervals.900") },
    { value: "1800", label: t("intervals.1800") },
  ];

  const rclone = parseRclone(settings?.rclone);
  const [pathPreview, setPathPreview] = useState<{
    destination?: string;
    error?: string;
    timeZone?: string;
    accountName?: string;
    accountUsername?: string;
    usingAccount?: boolean;
  } | null>(null);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void request("/settings/rclone-path-preview", {
        method: "POST",
        body: JSON.stringify({
          remote: rclone.remote,
          pathTemplate: rclone.pathTemplate,
          timeZone: rclone.timeZone,
          telegramId: account?.id ?? "",
        }),
      })
        .then((result) => setPathPreview(result))
        .catch((error: unknown) =>
          setPathPreview({
            error:
              error instanceof Error
                ? translatePreviewError(error.message, tErrors)
                : t("previewFailed"),
          }),
        );
    }, 250);
    return () => window.clearTimeout(handle);
  }, [account?.id, rclone.remote, rclone.pathTemplate, rclone.timeZone, t, tErrors]);

  const updateRclone = (patch: Partial<RcloneSettings>) => {
    void setSetting("rclone", JSON.stringify({ ...rclone, ...patch }));
  };

  const handleSave = async (e: FormEvent) => {
    e.preventDefault();
    await updateSettings();
  };

  const handleSwitchChange = (
    key: SettingKey,
    event?: React.MouseEvent<HTMLDivElement>,
  ) => {
    if (event && event.target instanceof HTMLInputElement) return;
    event?.stopPropagation();
    void setSetting(key, String(!(settings?.[key] === "true")));
  };

  return (
    <form
      onSubmit={handleSave}
      className="flex h-full flex-col overflow-hidden"
    >
      <div className="no-scrollbar flex flex-col space-y-4 overflow-y-scroll">
        <p className="rounded-md bg-gray-50 p-2 text-sm text-muted-foreground dark:bg-gray-700">
          <Bell className="mr-2 inline-block h-4 w-4" />
          {t("appliedToAll")}
        </p>
        <div className="flex w-full flex-col space-y-2 rounded-md border p-4 shadow">
          <Label htmlFor="ui-language">{t("language")}</Label>
          <Select
            value={locale}
            onValueChange={(value) => {
              if (isLocale(value)) setLocale(value);
            }}
          >
            <SelectTrigger id="ui-language">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="zh-CN">{t("zhCN")}</SelectItem>
              <SelectItem value="en">{t("en")}</SelectItem>
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">{t("languageHint")}</p>
        </div>
        <div className="w-full rounded-md border p-4 shadow">
          <p className="mb-1 text-xs text-muted-foreground">{t("rootPath")}</p>
          <div className="flex items-center justify-between space-x-1">
            <p className="rounded-md bg-gray-50 p-2 text-xs text-muted-foreground dark:bg-gray-700">
              {account?.rootPath}
            </p>
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.preventDefault();
                void copyToClipboard(account?.rootPath ?? "");
              }}
            >
              <Copy className="h-4 w-4" />
              <span className="sr-only">{t("copyPath")}</span>
            </Button>
          </div>
        </div>
        <div className="flex w-full cursor-pointer flex-col space-y-4 rounded-md border p-4 shadow">
          <div className="flex items-center justify-between">
            <Label>{t("speedUnits")}</Label>
            <RadioGroup
              value={settings?.speedUnits || "bits"}
              onValueChange={(v) => void setSetting("speedUnits", v)}
              className="group inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground"
              data-state={settings?.speedUnits || "bits"}
            >
              <label className="inline-flex cursor-pointer items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 group-data-[state=bits]:bg-background group-data-[state=bits]:text-foreground group-data-[state=bits]:shadow">
                {tCommon("bits")}
                <RadioGroupItem
                  id="enspeedUnits-bits"
                  value="bits"
                  className="sr-only"
                />
              </label>
              <label className="inline-flex cursor-pointer items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 group-data-[state=bytes]:bg-background group-data-[state=bytes]:text-foreground group-data-[state=bytes]:shadow">
                {tCommon("bytes")}
                <RadioGroupItem
                  id="speedUnits-bytes"
                  value="bytes"
                  className="sr-only"
                />
              </label>
            </RadioGroup>
          </div>
        </div>
        <div
          className="flex w-full cursor-pointer flex-col space-y-4 rounded-md border p-4 shadow"
          onClick={(event) => handleSwitchChange("uniqueOnly", event)}
        >
          <div className="flex items-center justify-between">
            <Label>{t("uniqueOnly")}</Label>
            <Switch
              id="unique-only"
              checked={settings?.uniqueOnly === "true"}
              onCheckedChange={() => handleSwitchChange("uniqueOnly")}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            {t("uniqueOnlyHint")} <br />
            <strong>{t("uniqueOnlyWarning")}</strong> {t("uniqueOnlyWarningBody")}
          </p>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <div
            className="flex cursor-pointer flex-col space-y-4"
            onClick={(event) => handleSwitchChange("alwaysHide", event)}
          >
            <div className="flex items-center justify-between">
              <Label>{t("alwaysHide")}</Label>
              <Switch
                id="always-hide"
                checked={settings?.alwaysHide === "true"}
                onCheckedChange={() => handleSwitchChange("alwaysHide")}
              />
            </div>
            <p className="text-xs text-muted-foreground">
              {t("alwaysHideHint")}
            </p>
          </div>
          {settings?.alwaysHide === "false" && (
            <div
              className="flex cursor-pointer flex-col space-y-4"
              onClick={(event) =>
                handleSwitchChange("showSensitiveContent", event)
              }
            >
              <div className="flex items-center justify-between">
                <Label>{t("showSensitive")}</Label>
                <Switch
                  id="show-sensitive-content"
                  checked={settings?.showSensitiveContent === "true"}
                  onCheckedChange={() =>
                    handleSwitchChange("showSensitiveContent")
                  }
                />
              </div>
              <p className="text-xs text-muted-foreground">
                {t("showSensitiveHint")}
              </p>
            </div>
          )}
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <Label>{t("autoDownload")}</Label>
          <div className="flex flex-col space-y-4">
            <div className="flex items-center justify-between">
              <Label htmlFor="limit">{t("limitPerAccount")}</Label>
              <span className="text-muted-foreground">
                {settings?.autoDownloadLimit ?? 5} / 10
              </span>
            </div>
            <Slider
              value={[Number(settings?.autoDownloadLimit ?? 5)]}
              onValueChange={(v) => {
                void setSetting("autoDownloadLimit", String(v[0]));
              }}
              min={1}
              max={10}
              step={1}
              className="w-full"
            />
            <p className="text-xs text-muted-foreground">
              {t("limitHint")}
            </p>
          </div>
          <div className="flex flex-col space-y-4">
            <Label htmlFor="avg-speed-interval">{t("avgSpeedInterval")}</Label>
            <Select
              value={String(settings?.avgSpeedInterval)}
              onValueChange={(v) => void setSetting("avgSpeedInterval", v)}
            >
              <SelectTrigger id="avg-speed-interval">
                <SelectValue placeholder={t("avgSpeedPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {avgSpeedIntervalOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              {t("avgSpeedHint")}
            </p>
          </div>
          <div className="flex flex-col space-y-4">
            <Label htmlFor="time-limited">{t("timeLimited")}</Label>
            <TimeRangeSelector
              startRequired={true}
              endRequired={true}
              includeSeconds={false}
              timeRange={
                settings?.autoDownloadTimeLimited
                  ? JSON.parse(settings.autoDownloadTimeLimited)
                  : { startTime: "00:00", endTime: "00:00" }
              }
              onTimeRangeChange={(
                startTime: string | null,
                endTime: string | null,
              ) => {
                void setSetting(
                  "autoDownloadTimeLimited",
                  JSON.stringify({
                    startTime: startTime ?? "00:00",
                    endTime: endTime ?? "00:00",
                  }),
                );
              }}
              className="max-w-md"
            />
            <p className="text-xs text-muted-foreground">
              {t("timeLimitedHint")}
            </p>
          </div>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <div className="flex items-center justify-between">
            <Label htmlFor="rclone-enabled">{t("rcloneTitle")}</Label>
            <Switch
              id="rclone-enabled"
              checked={rclone.enabled}
              onCheckedChange={(checked) => updateRclone({ enabled: checked })}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            {t("rcloneHint")}
          </p>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-remote">{t("remotePath")}</Label>
            <Input
              id="rclone-remote"
              value={rclone.remote}
              placeholder="gdrive:telegram"
              onChange={(event) => updateRclone({ remote: event.target.value })}
            />
            <p className="text-xs text-muted-foreground">
              {t("remoteHint")}
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-template">{t("pathTemplate")}</Label>
            <Input
              id="rclone-template"
              value={rclone.pathTemplate}
              placeholder="{chatId}/{messageId}/{fileName}"
              onChange={(event) =>
                updateRclone({ pathTemplate: event.target.value })
              }
            />
            <p className="text-xs text-muted-foreground">
              {t("pathTemplateIntro")}{" "}
              <code>{"{date:yyyyMMdd}"}</code>,{" "}
              <code>{"{date:yyyy-MM-dd}"}</code>, <code>{"{accountName}"}</code>,{" "}
              <code>{"{accountUsername}"}</code>, <code>{"{accountId}"}</code>,{" "}
              <code>{"{chatTitle}"}</code>, <code>{"{chatId}"}</code>,{" "}
              <code>{"{messageId}"}</code>, <code>{"{type}"}</code>,{" "}
              <code>{"{fileName}"}</code>. {t("pathTemplateDate")}
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-timezone">{t("dateTimezone")}</Label>
            <Input
              id="rclone-timezone"
              value={rclone.timeZone}
              placeholder="Asia/Shanghai"
              onChange={(event) =>
                updateRclone({ timeZone: event.target.value })
              }
            />
            <p className="text-xs text-muted-foreground">
              {t("timezoneHint")}
            </p>
          </div>
          <div className="rounded-md bg-gray-50 p-2 text-xs dark:bg-gray-700">
            <p className="text-muted-foreground">{t("preview")}</p>
            {pathPreview?.error ? (
              <p className="mt-1 break-all text-red-600">
                {translatePreviewError(pathPreview.error, tErrors)}
              </p>
            ) : (
              <p className="mt-1 break-all font-mono">
                {pathPreview?.destination ?? "…"}
              </p>
            )}
            <p className="mt-1 text-muted-foreground">
              {pathPreview?.usingAccount
                ? t("previewAccount", {
                    name:
                      pathPreview.accountName ||
                      pathPreview.accountUsername ||
                      "",
                    username:
                      pathPreview.accountUsername &&
                      pathPreview.accountUsername !== String(account?.id)
                        ? t("previewUsername", {
                            username: pathPreview.accountUsername,
                          })
                        : "",
                  })
                : t("previewNoAccount")}
              {t("previewSamples", {
                zone: pathPreview?.timeZone ?? "…",
              })}
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-config">{t("configFile")}</Label>
            <Input
              id="rclone-config"
              value={rclone.configPath}
              placeholder="/app/data/rclone/rclone.conf"
              onChange={(event) =>
                updateRclone({ configPath: event.target.value })
              }
            />
          </div>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-flags">{t("extraFlags")}</Label>
            <Input
              id="rclone-flags"
              value={rclone.extraFlags}
              placeholder='--transfers 4 --exclude "*.jpg"'
              onChange={(event) =>
                updateRclone({ extraFlags: event.target.value })
              }
            />
          </div>
          <div className="flex flex-col space-y-2">
            <Label>{t("operation")}</Label>
            <RadioGroup
              value={rclone.operation}
              onValueChange={(value) =>
                updateRclone({ operation: value as RcloneSettings["operation"] })
              }
              className="flex gap-4"
            >
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="copy" id="rclone-copy" />
                {t("copy")}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="move" id="rclone-move" />
                {t("move")}
              </label>
            </RadioGroup>
          </div>
          <div className="flex flex-col space-y-2">
            <Label>{t("when")}</Label>
            <RadioGroup
              value={rclone.mode}
              onValueChange={(value) =>
                updateRclone({ mode: value as RcloneSettings["mode"] })
              }
              className="flex flex-col gap-2"
            >
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="immediate" id="rclone-immediate" />
                {t("immediate")}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="scheduled" id="rclone-scheduled" />
                {t("scheduled")}
              </label>
            </RadioGroup>
            {rclone.mode === "scheduled" && (
              <Input
                id="rclone-schedule"
                value={rclone.schedule}
                placeholder="3600 or 0 */6 * * *"
                onChange={(event) =>
                  updateRclone({ schedule: event.target.value })
                }
              />
            )}
            <p className="text-xs text-muted-foreground">
              {t("scheduleHint")}
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label>{t("fileTypes")}</Label>
            <div className="flex flex-wrap gap-3">
              {RCLONE_FILE_TYPES.map((type) => {
                const selected = rclone.fileTypes
                  .split(",")
                  .map((item) => item.trim())
                  .filter(Boolean);
                const checked = selected.includes(type);
                return (
                  <label key={type} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={checked}
                      onCheckedChange={(value) => {
                        const next = new Set(selected);
                        if (value) {
                          next.add(type);
                        } else {
                          next.delete(type);
                        }
                        updateRclone({
                          fileTypes: RCLONE_FILE_TYPES.filter((item) =>
                            next.has(item),
                          ).join(","),
                        });
                      }}
                    />
                    {tType(type)}
                  </label>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              {t("fileTypesHint")}
            </p>
          </div>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <Label>{t("tags")}</Label>
          <div className="flex flex-col space-y-4">
            <TagsInput
              maxTags={20}
              value={
                (settings?.tags?.length ?? 0 > 0)
                  ? split(settings?.tags, ",")
                  : []
              }
              onChange={(tags) => void setSetting("tags", tags.join(","))}
            />
          </div>
        </div>
      </div>
      <DialogFooter className="mt-2 flex-1 gap-2">
        <DialogClose asChild>
          <Button className="w-full md:w-auto" variant="outline" type="button">
            {tCommon("cancel")}
          </Button>
        </DialogClose>
        <Button className="w-full md:w-auto" type="submit">
          {tCommon("submit")}
        </Button>
      </DialogFooter>
    </form>
  );
}
