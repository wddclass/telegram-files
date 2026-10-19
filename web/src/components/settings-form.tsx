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
import React, { type FormEvent } from "react";
import { useSettings } from "@/hooks/use-settings";
import { useTelegramAccount } from "@/hooks/use-telegram-account";
import { useCopyToClipboard } from "@/hooks/use-copy-to-clipboard";
import { DialogClose, DialogFooter } from "@/components/ui/dialog";
import TimeRangeSelector from "@/components/ui/time-range-selector";
import { Switch } from "@/components/ui/switch";
import { type SettingKey } from "@/lib/types";

type RcloneSettings = {
  enabled: boolean;
  remote: string;
  configPath: string;
  extraFlags: string;
  operation: "copy" | "move";
  mode: "immediate" | "scheduled";
  schedule: string;
  fileTypes: string;
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

  const avgSpeedIntervalOptions = [
    { value: "60", label: "1 minute" },
    { value: "300", label: "5 minutes" },
    { value: "600", label: "10 minutes" },
    { value: "900", label: "15 minutes" },
    { value: "1800", label: "30 minutes" },
  ];

  const rclone = parseRclone(settings?.rclone);

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
          These settings will be applied to all accounts.
        </p>
        <div className="w-full rounded-md border p-4 shadow">
          <p className="mb-1 text-xs text-muted-foreground">Your root path</p>
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
            </Button>
          </div>
        </div>
        <div className="flex w-full cursor-pointer flex-col space-y-4 rounded-md border p-4 shadow">
          <div className="flex items-center justify-between">
            <Label>Speed Units</Label>
            <RadioGroup
              value={settings?.speedUnits || "bits"}
              onValueChange={(v) => void setSetting("speedUnits", v)}
              className="group inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground"
              data-state={settings?.speedUnits || "bits"}
            >
              <label className="inline-flex cursor-pointer items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 group-data-[state=bits]:bg-background group-data-[state=bits]:text-foreground group-data-[state=bits]:shadow">
                bits
                <RadioGroupItem
                  id="enspeedUnits-bits"
                  value="bits"
                  className="sr-only"
                />
              </label>
              <label className="inline-flex cursor-pointer items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 group-data-[state=bytes]:bg-background group-data-[state=bytes]:text-foreground group-data-[state=bytes]:shadow">
                bytes
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
            <Label>Unique Only</Label>
            <Switch
              id="unique-only"
              checked={settings?.uniqueOnly === "true"}
              onCheckedChange={() => handleSwitchChange("uniqueOnly")}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Show only unique file in the table. If disabled, will show all.{" "}
            <br />
            <strong>Warning:</strong> If enabled, the number of documents on the
            form will be inaccurate.
          </p>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <div
            className="flex cursor-pointer flex-col space-y-4"
            onClick={(event) => handleSwitchChange("alwaysHide", event)}
          >
            <div className="flex items-center justify-between">
              <Label>Always Hide</Label>
              <Switch
                id="always-hide"
                checked={settings?.alwaysHide === "true"}
                onCheckedChange={() => handleSwitchChange("alwaysHide")}
              />
            </div>
            <p className="text-xs text-muted-foreground">
              Always hide content and extra info in the table.
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
                <Label>Show Sensitive Content</Label>
                <Switch
                  id="show-sensitive-content"
                  checked={settings?.showSensitiveContent === "true"}
                  onCheckedChange={() =>
                    handleSwitchChange("showSensitiveContent")
                  }
                />
              </div>
              <p className="text-xs text-muted-foreground">
                Show sensitive content in the table, Will use a spoiler to hide
                sensitive content if disabled.
              </p>
            </div>
          )}
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <Label>Auto Download Settings</Label>
          <div className="flex flex-col space-y-4">
            <div className="flex items-center justify-between">
              <Label htmlFor="limit">Limit Per Account</Label>
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
              The maximum number of files to download per account. <br />
              This is useful for limiting the number of concurrent downloads.
              Including the number of downloads you manually.
            </p>
          </div>
          <div className="flex flex-col space-y-4">
            <Label htmlFor="avg-speed-interval">Avg Speed Interval</Label>
            <Select
              value={String(settings?.avgSpeedInterval)}
              onValueChange={(v) => void setSetting("avgSpeedInterval", v)}
            >
              <SelectTrigger id="avg-speed-interval">
                <SelectValue placeholder="Select Avg Speed Interval" />
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
              The interval to calculate the average download speed. <br />
              Longer intervals may consume more memory.
            </p>
          </div>
          <div className="flex flex-col space-y-4">
            <Label htmlFor="time-limited">Time Limited</Label>
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
              The time range for the download. Start and end times are required.{" "}
              <br />
              If you don&#39;t want to set a time range, you can set the start
              and end to 00:00.
            </p>
          </div>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <div className="flex items-center justify-between">
            <Label htmlFor="rclone-enabled">Rclone upload</Label>
            <Switch
              id="rclone-enabled"
              checked={rclone.enabled}
              onCheckedChange={(checked) => updateRclone({ enabled: checked })}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Optional. Off by default. After a file is downloaded, copy or move
            it with rclone (for example to Google Drive) without blocking the
            download. Files already uploaded are skipped.
          </p>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-remote">Remote path</Label>
            <Input
              id="rclone-remote"
              value={rclone.remote}
              placeholder="gdrive:telegram"
              onChange={(event) => updateRclone({ remote: event.target.value })}
            />
            <p className="text-xs text-muted-foreground">
              Destination remote, for example <code>gdrive:telegram</code>.
              Files are stored under{" "}
              <code>remote/chatId/messageId/fileName</code>.
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label htmlFor="rclone-config">rclone config file</Label>
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
            <Label htmlFor="rclone-flags">Extra flags</Label>
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
            <Label>Operation</Label>
            <RadioGroup
              value={rclone.operation}
              onValueChange={(value) =>
                updateRclone({ operation: value as RcloneSettings["operation"] })
              }
              className="flex gap-4"
            >
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="copy" id="rclone-copy" />
                Copy
              </label>
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="move" id="rclone-move" />
                Move (delete local after success)
              </label>
            </RadioGroup>
          </div>
          <div className="flex flex-col space-y-2">
            <Label>When to upload</Label>
            <RadioGroup
              value={rclone.mode}
              onValueChange={(value) =>
                updateRclone({ mode: value as RcloneSettings["mode"] })
              }
              className="flex flex-col gap-2"
            >
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="immediate" id="rclone-immediate" />
                Immediately after each download completes
              </label>
              <label className="flex items-center gap-2 text-sm">
                <RadioGroupItem value="scheduled" id="rclone-scheduled" />
                Scheduled
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
              Schedule is a number of seconds (minimum 30) or a 5-field cron
              expression, for example <code>0 */6 * * *</code>. Scheduled mode
              uploads every completed file that matches the filter and has not
              been uploaded yet.
            </p>
          </div>
          <div className="flex flex-col space-y-2">
            <Label>File types</Label>
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
                    {type}
                  </label>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              Defaults to videos. Leave a type unchecked to skip it.
            </p>
          </div>
        </div>
        <div className="flex w-full flex-col space-y-4 rounded-md border p-4 shadow">
          <Label>Tags Settings</Label>
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
            Cancel
          </Button>
        </DialogClose>
        <Button className="w-full md:w-auto" type="submit">
          Submit
        </Button>
      </DialogFooter>
    </form>
  );
}
