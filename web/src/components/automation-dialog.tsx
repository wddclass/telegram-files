import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import React, { useEffect, useState } from "react";
import useSWRMutation from "swr/mutation";
import { POST } from "@/lib/api";
import { useDebounce } from "use-debounce";
import { useToast } from "@/hooks/use-toast";
import { AutomationButton } from "@/components/automation-button";
import { useTelegramChat } from "@/hooks/use-telegram-chat";
import { useTelegramAccount } from "@/hooks/use-telegram-account";
import { Label } from "@/components/ui/label";
import { type Auto } from "@/lib/types";
import { Badge } from "./ui/badge";
import { cn } from "@/lib/utils";
import AutomationForm from "@/components/automation-form";
import { useTranslations } from "next-intl";
import { isFileTypeKey } from "@/i18n/labels";

const DEFAULT_AUTO: Auto = {
  preload: {
    enabled: false,
  },
  download: {
    enabled: false,
    rule: {
      query: "",
      fileTypes: [],
      downloadHistory: true,
      downloadCommentFiles: false,
      filterExpr: "",
    },
  },
  transfer: {
    enabled: false,
    rule: {
      transferHistory: true,
      destination: "",
      transferPolicy: "GROUP_BY_CHAT",
      duplicationPolicy: "OVERWRITE",
      useCaptionName: false,
      extra: {},
    },
  },
};

export default function AutomationDialog() {
  const { accountId } = useTelegramAccount();
  const { isLoading, chat, reload } = useTelegramChat();
  const { toast } = useToast();
  const t = useTranslations("automation");
  const tc = useTranslations("common");
  const tType = useTranslations("fileType");
  const policyTitle = (policy: string) => {
    switch (policy) {
      case "DIRECT":
        return t("policies.DIRECT.title");
      case "GROUP_BY_CHAT":
        return t("policies.GROUP_BY_CHAT.title");
      case "GROUP_BY_TYPE":
        return t("policies.GROUP_BY_TYPE.title");
      case "GROUP_BY_AI":
        return t("policies.GROUP_BY_AI.title");
      case "OVERWRITE":
        return t("policies.OVERWRITE.title");
      case "SKIP":
        return t("policies.SKIP.title");
      case "RENAME":
        return t("policies.RENAME.title");
      case "HASH":
        return t("policies.HASH.title");
      default:
        return policy;
    }
  };
  const [open, setOpen] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [auto, setAuto] = useState<Auto>(DEFAULT_AUTO);
  const { trigger: triggerAuto, isMutating: isAutoMutating } = useSWRMutation(
    !accountId || !chat
      ? undefined
      : `/${accountId}/file/update-auto-settings?telegramId=${accountId}&chatId=${chat?.id}`,
    (
      key,
      {
        arg,
      }: {
        arg: Auto;
      },
    ) => {
      return POST(key, arg);
    },
    {
      onSuccess: () => {
        toast({
          variant: "success",
          title: t("updated"),
        });
        void reload();
        setEditMode(false);
        setTimeout(() => {
          setOpen(false);
        }, 1000);
      },
    },
  );

  const [debounceIsAutoMutating] = useDebounce(isAutoMutating, 500, {
    leading: true,
  });

  useEffect(() => {
    if (chat?.auto) {
      setAuto(chat.auto);
    } else {
      setAuto(DEFAULT_AUTO);
    }
  }, [chat]);

  if (isLoading) {
    return (
      <div className="h-8 w-32 animate-pulse bg-gray-200 dark:bg-gray-700"></div>
    );
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        asChild
        onClick={(e) => {
          e.stopPropagation();
          setOpen(!open);
        }}
      >
        {chat && <AutomationButton auto={chat.auto} />}
      </DialogTrigger>
      <DialogContent
        aria-describedby={undefined}
        onPointerDownOutside={() => setOpen(false)}
        onClick={(e) => e.stopPropagation()}
        className="h-full w-full overflow-auto md:h-auto md:max-h-[85%] md:min-w-[400px]"
      >
        <DialogHeader>
          <DialogTitle>
            {t("title", { name: chat?.name ?? t("unknownChat") })}
          </DialogTitle>
        </DialogHeader>
        <DialogDescription></DialogDescription>
        {!editMode && chat?.auto ? (
          <div className="space-y-4">
            <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-semibold text-gray-900 dark:text-gray-300">
                  {t("preload")}
                </Label>
                <Badge
                  variant="outline"
                  className={cn(
                    "border-none bg-green-500 px-2 py-0.5 text-xs text-white dark:bg-green-800 dark:text-green-200",
                    chat.auto.preload.enabled
                      ? "bg-green-500 dark:bg-green-800 dark:text-green-200"
                      : "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                  )}
                >
                  {chat.auto.preload.enabled ? tc("enabled") : tc("disabled")}
                </Badge>
              </div>
              {(chat.auto.state & (1 << 1)) != 0 && (
                <p className="text-xs text-muted-foreground">
                  {t("preloadDone")}
                </p>
              )}
            </div>
            <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-semibold text-gray-900 dark:text-gray-300">
                  {t("download")}
                </Label>
                <Badge
                  variant="outline"
                  className={cn(
                    "border-none px-2 py-0.5 text-xs text-white",
                    chat.auto.download.enabled
                      ? "bg-green-500 dark:bg-green-800 dark:text-green-200"
                      : "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                  )}
                >
                  {chat.auto.download.enabled ? tc("enabled") : tc("disabled")}
                </Badge>
              </div>
              {auto.download.enabled && (
                <>
                  {(chat.auto.state & (1 << 2)) != 0 && (
                    <p className="text-xs text-muted-foreground">
                      {t("downloadStarted")}
                    </p>
                  )}
                  <div className="space-y-3">
                    {/* Query Keyword Section */}
                    <div className="rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <div className="flex flex-col space-y-1">
                        <span className="text-xs font-medium text-gray-500">
                          {t("query")}
                        </span>
                        <span className="text-sm text-gray-500 dark:text-gray-300">
                          {chat.auto.download.rule.query ||
                            t("noQuery")}
                        </span>
                      </div>
                    </div>
                    <div className="rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <div className="flex flex-col space-y-1">
                        <span className="text-xs font-medium text-gray-500">
                          {t("filterExpr")}
                        </span>
                        <span className="text-sm text-gray-500 dark:text-gray-300">
                          {chat.auto.download.rule.filterExpr ||
                            t("noFilter")}
                        </span>
                      </div>
                    </div>

                    <div className="rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <span className="text-xs font-medium text-gray-500">
                        {t("fileTypes")}
                      </span>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {chat.auto.download.rule.fileTypes.length > 0 ? (
                          chat.auto.download.rule.fileTypes.map((type) => (
                            <Badge
                              key={type}
                              variant="secondary"
                              className="flex items-center gap-1 border-gray-200 bg-white px-3 py-1 capitalize text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                            >
                              {isFileTypeKey(type) ? tType(type) : type}
                            </Badge>
                          ))
                        ) : (
                          <span className="text-sm text-gray-500 dark:text-gray-300">
                            {t("noFileTypes")}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center justify-between rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <span className="text-xs font-medium text-gray-500">
                        {t("downloadHistory")}
                      </span>
                      <Badge
                        className={cn(
                          "border-none px-2 py-0.5 text-xs text-white",
                          !chat.auto.download.rule.downloadHistory &&
                            "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                        )}
                      >
                        {chat.auto.download.rule.downloadHistory
                          ? tc("enabled")
                          : tc("disabled")}
                      </Badge>
                    </div>

                    <div className="flex items-center justify-between rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <span className="text-xs font-medium text-gray-500">
                        {t("downloadComments")}
                      </span>
                      <Badge
                        className={cn(
                          "border-none px-2 py-0.5 text-xs text-white",
                          !chat.auto.download.rule.downloadCommentFiles &&
                            "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                        )}
                      >
                        {chat.auto.download.rule.downloadCommentFiles
                          ? tc("enabled")
                          : tc("disabled")}
                      </Badge>
                    </div>
                  </div>
                </>
              )}
            </div>

            <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-semibold text-gray-900 dark:text-gray-300">
                  {t("transfer")}
                </Label>
                <Badge
                  variant="outline"
                  className={cn(
                    "border-none px-2 py-0.5 text-xs text-white",
                    chat.auto.transfer.enabled
                      ? "bg-green-500 dark:bg-green-800 dark:text-green-200"
                      : "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                  )}
                >
                  {chat.auto.transfer.enabled ? tc("enabled") : tc("disabled")}
                </Badge>
              </div>
              {chat.auto.transfer.enabled && (
                <>
                  {(chat.auto.state & (1 << 4)) != 0 && (
                    <p className="text-xs text-muted-foreground">
                      {t("transferDone")}
                    </p>
                  )}
                  <div className="space-y-3">
                    <div className="rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <div className="flex flex-col space-y-1">
                        <span className="text-xs font-medium text-gray-500">
                          {t("destination")}
                        </span>
                        <span className="text-sm text-gray-500 dark:text-gray-300">
                          {chat.auto.transfer.rule.destination}
                        </span>
                      </div>
                    </div>
                    <div className="flex flex-col space-y-3 rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-gray-500 dark:text-gray-300">
                          {t("transferPolicy")}
                        </span>
                        <Badge variant="outline" className="font-normal">
                          {policyTitle(chat.auto.transfer.rule.transferPolicy)}
                        </Badge>
                      </div>
                      {chat.auto.transfer.rule.transferPolicy ===
                        "GROUP_BY_AI" && (
                        <div className="mt-2 w-full whitespace-pre-line rounded-md bg-gray-100 p-2 text-xs text-gray-500 dark:bg-gray-700 dark:text-gray-300">
                          {chat.auto.transfer.rule.extra.promptTemplate}
                        </div>
                      )}
                    </div>
                    <div className="flex items-center justify-between rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <span className="text-xs text-gray-500 dark:text-gray-300">
                        {t("duplicationPolicy")}
                      </span>
                      <Badge variant="outline" className="font-normal">
                        {policyTitle(chat.auto.transfer.rule.duplicationPolicy)}
                      </Badge>
                    </div>
                    <div className="flex items-center justify-between rounded-lg bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800">
                      <span className="text-xs text-gray-500 dark:text-gray-300">
                        {t("transferHistory")}
                      </span>
                      <Badge
                        className={cn(
                          "border-none px-2 py-0.5 text-xs text-white",
                          !chat.auto.transfer.rule.transferHistory &&
                            "bg-gray-500 dark:bg-gray-800 dark:text-gray-300",
                        )}
                      >
                        {chat.auto.transfer.rule.transferHistory
                          ? tc("enabled")
                          : tc("disabled")}
                      </Badge>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        ) : (
          <AutomationForm auto={auto} onChange={setAuto} />
        )}
        <DialogFooter className="gap-2">
          {!editMode && chat?.auto ? (
            <Button variant="outline" onClick={() => setEditMode(true)}>
              {tc("edit")}
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                onClick={() => setOpen(false)}
                disabled={debounceIsAutoMutating}
              >
                {tc("cancel")}
              </Button>
              <Button
                onClick={() => {
                  const folderPathRegex =
                    /^[\/\\]?(?:[^<>:"|?*\/\\]+[\/\\]?)*$/;
                  if (
                    auto?.transfer.enabled &&
                    (auto?.transfer.rule.destination.length === 0 ||
                      !folderPathRegex.test(auto?.transfer.rule.destination))
                  ) {
                    toast({
                      variant: "warning",
                      title: t("invalidFolder"),
                      description: t("invalidFolderHint"),
                    });
                    return;
                  }
                  void triggerAuto(auto);
                }}
                disabled={debounceIsAutoMutating}
              >
                {debounceIsAutoMutating ? t("submitting") : tc("submit")}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
