import { Button } from "@/components/ui/button";
import {
  Download,
  FileX,
  LoaderCircle,
  Pause,
  SquareX,
  StepForward,
} from "lucide-react";
import React, { useState } from "react";
import useSWRMutation from "swr/mutation";
import { POST } from "@/lib/api";
import { type TelegramFile } from "@/lib/types";
import { TooltipWrapper } from "@/components/ui/tooltip";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "./ui/dialog";
import { toast } from "@/hooks/use-toast";
import { BatchFileTags } from "@/components/file-tags";
import { useTranslations } from "next-intl";

type BatchAction = "download" | "continue" | "pause" | "cancel" | "delete";

interface FileBatchControlProps {
  selectedFiles: Set<number>;
  setSelectedFiles: (files: Set<number>) => void;
  files: TelegramFile[];
  updateField?: (
    uniqueId: string,
    patch: Partial<TelegramFile>,
  ) => Promise<void>;
}

export default function FileBatchControl({
  selectedFiles,
  setSelectedFiles,
  files,
  updateField,
}: FileBatchControlProps) {
  const t = useTranslations("files");
  const tb = useTranslations("files.batch");
  const selectedFileObjects = Array.from(selectedFiles)
    .map((id) => files.find((f) => f.id === id))
    .filter(Boolean) as TelegramFile[];

  // Calculate counts for different file states
  const downloadableCounts = selectedFileObjects.filter(
    (file) => file.downloadStatus === "idle",
  ).length;
  const pausableCounts = selectedFileObjects.filter(
    (file) => file.downloadStatus === "downloading",
  ).length;
  const continuableCounts = selectedFileObjects.filter(
    (file) => file.downloadStatus === "paused",
  ).length;
  const cancelableCounts = selectedFileObjects.filter(
    (file) => file.downloadStatus === "downloading",
  ).length;
  const deletableCounts = selectedFileObjects.filter(
    (file) => file.downloadStatus === "completed",
  ).length;
  const loadedFiles = selectedFileObjects.filter((file) => file.loaded);

  const controlButtons: Array<{
    url: string;
    action: BatchAction;
    label: string;
    tooltip: string;
    className?: string;
    icon: React.ReactNode;
    filter: (file: TelegramFile) => boolean;
    validCount: number;
    showConfirm: boolean;
  }> = [
    {
      url: "/files/start-download-multiple",
      action: "download",
      label: tb("download"),
      tooltip: tb("tooltipDownload", { count: downloadableCounts }),
      icon: <Download className="mr-2 h-4 w-4" />,
      filter: (file: TelegramFile) => file.downloadStatus === "idle",
      validCount: downloadableCounts,
      showConfirm: downloadableCounts > 5,
    },
    {
      url: "/files/toggle-pause-download-multiple",
      action: "continue",
      label: tb("continue"),
      tooltip: tb("tooltipContinue", { count: continuableCounts }),
      className: "bg-green-500 hover:bg-green-600 text-white",
      icon: <StepForward className="mr-2 h-4 w-4" />,
      filter: (file: TelegramFile) => file.downloadStatus === "paused",
      validCount: continuableCounts,
      showConfirm: false,
    },
    {
      url: "/files/toggle-pause-download-multiple",
      action: "pause",
      label: tb("pause"),
      tooltip: tb("tooltipPause", { count: pausableCounts }),
      className: "bg-yellow-500 hover:bg-yellow-600 text-white",
      icon: <Pause className="mr-2 h-4 w-4" />,
      filter: (file: TelegramFile) => file.downloadStatus === "downloading",
      validCount: pausableCounts,
      showConfirm: false,
    },
    {
      url: "/files/cancel-download-multiple",
      action: "cancel",
      label: tb("cancel"),
      tooltip: tb("tooltipCancel", { count: cancelableCounts }),
      className: "bg-red-500 hover:bg-red-600 text-white",
      icon: <SquareX className="mr-2 h-4 w-4" />,
      filter: (file: TelegramFile) => file.downloadStatus === "downloading",
      validCount: cancelableCounts,
      showConfirm: true,
    },
    {
      url: "/files/remove-multiple",
      action: "delete",
      label: tb("delete"),
      tooltip: tb("tooltipDelete", { count: deletableCounts }),
      className: "bg-red-500 hover:bg-red-600 text-white",
      icon: <FileX className="mr-2 h-4 w-4" />,
      filter: (file: TelegramFile) => file.downloadStatus === "completed",
      validCount: deletableCounts,
      showConfirm: true,
    },
  ];

  const handleTagsUpdate = (tags: string[]) => {
    if (updateField) {
      loadedFiles.forEach((file) => {
        const newTags = tags.join(",");
        void updateField(file.uniqueId, { tags: newTags });
        setSelectedFiles(new Set());
      });
    }
  };

  // Filter buttons to only show those that have at least one valid file
  const visibleButtons = controlButtons.filter(
    (button) => button.validCount > 0,
  );

  return (
    <>
      {selectedFiles.size > 0 && (
        <div className="flex flex-col rounded-lg bg-muted/50 p-4 transition-all duration-300 animate-in slide-in-from-bottom-2 md:flex-row md:items-center md:justify-between">
          <span className="mb-3 text-sm font-medium md:mb-0">
            {t("selected", { count: selectedFiles.size })}
          </span>
          <div className="flex flex-wrap gap-2">
            {loadedFiles.length > 0 && (
              <BatchFileTags
                files={loadedFiles}
                onTagsUpdate={handleTagsUpdate}
              />
            )}
            {visibleButtons.map((button) => (
              <ControlButton
                key={button.action}
                selectedFiles={selectedFiles}
                setSelectedFiles={setSelectedFiles}
                files={files}
                {...button}
              />
            ))}
            <Button
              size="sm"
              variant="outline"
              onClick={() => setSelectedFiles(new Set())}
            >
              {t("clearSelection")}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

interface ControlButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  url: string;
  action: BatchAction;
  label: string;
  icon: React.ReactNode;
  tooltip: string;
  className?: string;
  extra?: Record<string, any>;
  filter: (file: TelegramFile) => boolean;
  validCount: number;
  showConfirm: boolean;
  selectedFiles: Set<number>;
  setSelectedFiles: (files: Set<number>) => void;
  files: TelegramFile[];
}

function ControlButton({
  url,
  action,
  label,
  icon,
  tooltip,
  className,
  extra,
  filter,
  validCount,
  showConfirm,
  selectedFiles,
  setSelectedFiles,
  files,
}: ControlButtonProps) {
  const tb = useTranslations("files.batch");
  const tc = useTranslations("common");
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);

  const selectedFileObjects = Array.from(selectedFiles)
    .map((id) => files.find((f) => f.id === id))
    .filter(Boolean) as TelegramFile[];

  // Calculate valid and invalid files based on the filter
  const validFiles = selectedFileObjects.filter(filter);
  const invalidCount = selectedFiles.size - validFiles.length;

  const { trigger, isMutating } = useSWRMutation(
    url,
    (
      key,
      {
        arg,
      }: {
        arg: {
          files: Array<{
            telegramId: number;
            chatId: number;
            messageId: number;
            fileId: number;
            uniqueId: string;
          }>;
        } & Record<string, any>;
      },
    ) => POST(key, arg),
    {
      onSuccess: () => {
        setSelectedFiles(new Set());
        toast({
          title: tb("completed", { action: label }),
          description: tb("processed", { count: validFiles.length }),
          variant: "success",
        });
      },
    },
  );

  const handleAction = () => {
    void trigger({
      files: validFiles.map((file) => ({
        telegramId: file.telegramId ?? 0,
        chatId: file.chatId ?? 0,
        messageId: file.messageId ?? 0,
        fileId: file.id ?? 0,
        uniqueId: file.uniqueId,
      })),
      ...extra,
    });
    setConfirmDialogOpen(false);
  };

  const handleClick = () => {
    if (showConfirm) {
      setConfirmDialogOpen(true);
    } else {
      handleAction();
    }
  };

  return (
    <>
      <TooltipWrapper content={tooltip}>
        <Button
          size="sm"
          className={className}
          onClick={handleClick}
          disabled={validCount === 0 || isMutating}
        >
          {isMutating ? (
            <LoaderCircle
              className="mr-2 h-4 w-4 animate-spin"
              style={{ strokeWidth: "0.8px" }}
            />
          ) : (
            <>
              {icon}
              {label} {validCount > 0 && `(${validCount})`}
            </>
          )}
        </Button>
      </TooltipWrapper>

      <Dialog open={confirmDialogOpen} onOpenChange={setConfirmDialogOpen}>
        <DialogContent className="max-w-xl sm:max-w-md">
          <DialogHeader className="space-y-2">
            <DialogTitle className="text-center text-xl font-semibold">
              {tb("confirm", { action: label })}
            </DialogTitle>
            <div className="flex justify-center">
              {action === "delete" ? (
                <div className="rounded-full bg-red-100 p-3 dark:bg-red-900/30">
                  <FileX className="h-6 w-6 text-red-600 dark:text-red-400" />
                </div>
              ) : action === "cancel" ? (
                <div className="rounded-full bg-red-100 p-3 dark:bg-red-900/30">
                  <SquareX className="h-6 w-6 text-red-600 dark:text-red-400" />
                </div>
              ) : action === "download" ? (
                <div className="rounded-full bg-blue-100 p-3 dark:bg-blue-900/30">
                  <Download className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                </div>
              ) : action === "continue" ? (
                <div className="rounded-full bg-green-100 p-3 dark:bg-green-900/30">
                  <StepForward className="h-6 w-6 text-green-600 dark:text-green-400" />
                </div>
              ) : (
                <div className="rounded-full bg-yellow-100 p-3 dark:bg-yellow-900/30">
                  <Pause className="h-6 w-6 text-yellow-600 dark:text-yellow-400" />
                </div>
              )}
            </div>
          </DialogHeader>

          <div className="pb-6 pt-2">
            <p className="mb-3 text-center text-sm text-muted-foreground">
              {tb("confirmBody", { action: label })}
            </p>

            <div className="mt-4 flex flex-col gap-3">
              {validCount > 0 && (
                <div className="overflow-hidden rounded-lg border border-green-200 dark:border-green-800">
                  <div className="border-b border-green-200 bg-green-50 px-4 py-2 dark:border-green-800 dark:bg-green-900/20">
                    <span className="text-sm font-medium text-green-800 dark:text-green-300">
                      {tb("toProcess")}
                    </span>
                  </div>
                  <div className="flex items-center bg-white p-4 dark:bg-background">
                    <div className="mr-3 flex h-8 w-8 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
                      <span className="text-sm font-semibold text-green-700 dark:text-green-300">
                        {validCount}
                      </span>
                    </div>
                    <div>
                      <p className="text-sm font-medium">
                        {tb("willProcess", { count: validCount })}
                      </p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {tb("compatible")}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {invalidCount > 0 && (
                <div className="overflow-hidden rounded-lg border border-red-200 dark:border-red-800">
                  <div className="border-b border-red-200 bg-red-50 px-4 py-2 dark:border-red-800 dark:bg-red-900/20">
                    <span className="text-sm font-medium text-red-800 dark:text-red-300">
                      {tb("skipped")}
                    </span>
                  </div>
                  <div className="flex items-center bg-white p-4 dark:bg-background">
                    <div className="mr-3 flex h-8 w-8 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
                      <span className="text-sm font-semibold text-red-700 dark:text-red-300">
                        {invalidCount}
                      </span>
                    </div>
                    <div>
                      <p className="text-sm font-medium">
                        {tb("cannotProcess", { count: invalidCount })}
                      </p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {tb("incompatible")}
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          <DialogFooter className="gap-3 sm:justify-center">
            <DialogClose asChild>
              <Button variant="outline" className="min-w-24">
                {tc("cancel")}
              </Button>
            </DialogClose>
            <Button
              onClick={handleAction}
              className={`min-w-24 ${
                action === "delete" || action === "cancel"
                  ? "bg-red-500 text-white hover:bg-red-600"
                  : action === "continue"
                    ? "bg-green-500 text-white hover:bg-green-600"
                    : action === "pause"
                      ? "bg-yellow-500 text-white hover:bg-yellow-600"
                      : ""
              }`}
            >
              {`${label}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
