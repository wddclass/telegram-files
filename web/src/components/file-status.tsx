import { type RcloneStatus, type TelegramFile } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import React, { useState } from "react";
import { cn } from "@/lib/utils";
import { TooltipWrapper } from "@/components/ui/tooltip";
import { AnimatePresence, motion } from "framer-motion";
import {
  CheckCircle2,
  Clock,
  CloudUpload,
  Download,
  FolderSync,
  Pause,
  RotateCcw,
  XCircle,
} from "lucide-react";
import useIsMobile from "@/hooks/use-is-mobile";
import { request } from "@/lib/api";
import { toast } from "@/hooks/use-toast";

export const DOWNLOAD_STATUS = {
  idle: {
    icon: Clock,
    className: "bg-gray-100 text-gray-600",
    text: "Idle",
  },
  downloading: {
    icon: Download,
    className: "bg-blue-100 text-blue-600",
    text: "Downloading",
  },
  paused: {
    icon: Pause,
    className: "bg-yellow-100 text-yellow-600",
    text: "Paused",
  },
  completed: {
    icon: CheckCircle2,
    className: "bg-green-100 text-green-600",
    text: "Completed",
  },
  error: {
    icon: XCircle,
    className: "bg-red-100 text-red-600",
    text: "Error",
  },
};

export const RCLONE_STATUS: Record<
  RcloneStatus,
  { icon: typeof Clock; className: string; text: string }
> = {
  pending: {
    icon: Clock,
    className: "bg-gray-100 text-gray-600",
    text: "Upload pending",
  },
  uploading: {
    icon: CloudUpload,
    className: "bg-blue-100 text-blue-600",
    text: "Uploading",
  },
  uploaded: {
    icon: CheckCircle2,
    className: "bg-green-100 text-green-600",
    text: "Uploaded",
  },
  failed: {
    icon: XCircle,
    className: "bg-red-100 text-red-600",
    text: "Upload failed",
  },
};

export const TRANSFER_STATUS = {
  idle: {
    icon: Clock,
    className: "bg-gray-100 text-gray-600",
    text: "Idle",
  },
  transferring: {
    icon: FolderSync,
    className: "bg-blue-100 text-blue-600",
    text: "Transferring",
  },
  completed: {
    icon: CheckCircle2,
    className: "bg-green-100 text-green-600",
    text: "Transferred",
  },
  error: {
    icon: XCircle,
    className: "bg-red-100 text-red-600",
    text: "Transfer Error",
  },
};

export default function FileStatus({
  file,
  className,
}: {
  file: TelegramFile;
  className?: string;
}) {
  const badgeVariants = {
    initial: { opacity: 0, scale: 0.9 },
    animate: {
      opacity: 1,
      scale: 1,
      transition: { type: "spring", stiffness: 300 },
    },
    exit: { opacity: 0, scale: 0.9, transition: { duration: 0.2 } },
  };
  const isMobile = useIsMobile();
  const [retrying, setRetrying] = useState(false);
  const rclone =
    file.rcloneStatus && file.rcloneStatus in RCLONE_STATUS
      ? RCLONE_STATUS[file.rcloneStatus]
      : undefined;

  async function retryUpload() {
    setRetrying(true);
    try {
      await request(`/file/${file.uniqueId}/rclone-retry`, {
        method: "POST",
      });
      toast({ variant: "success", description: "Upload queued again" });
    } catch (error) {
      toast({
        variant: "error",
        description:
          error instanceof Error ? error.message : "Failed to retry upload",
      });
    } finally {
      setRetrying(false);
    }
  }

  return (
    <div
      className={cn("flex items-center justify-center space-x-2", className)}
    >
      <AnimatePresence>
        {file.transferStatus === "idle" && (
          <motion.div
            key="download-status"
            variants={badgeVariants}
            initial="initial"
            animate="animate"
            exit="exit"
          >
            <TooltipWrapper content="Download Status">
              <Badge
                className={cn(
                  "h-6 text-xs hover:bg-gray-200",
                  DOWNLOAD_STATUS[file.downloadStatus].className,
                  isMobile && "shadow-none",
                )}
              >
                {DOWNLOAD_STATUS[file.downloadStatus].text}
              </Badge>
            </TooltipWrapper>
          </motion.div>
        )}
        {file.downloadStatus === "completed" &&
          file.transferStatus &&
          file.transferStatus !== "idle" && (
            <motion.div
              key="transfer-status"
              variants={badgeVariants}
              initial="initial"
              animate="animate"
              exit="exit"
            >
              <TooltipWrapper content="Transfer Status">
                <Badge
                  className={cn(
                    "h-6 text-xs hover:bg-gray-200",
                    TRANSFER_STATUS[file.transferStatus].className,
                  )}
                >
                  {TRANSFER_STATUS[file.transferStatus].text}
                </Badge>
              </TooltipWrapper>
            </motion.div>
          )}
        {rclone && (
          <motion.div
            key="rclone-status"
            variants={badgeVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="flex items-center space-x-1"
          >
            <TooltipWrapper
              content={
                file.rcloneStatus === "failed" && file.rcloneError
                  ? file.rcloneError
                  : "Rclone upload"
              }
            >
              <Badge
                className={cn(
                  "h-6 text-xs hover:bg-gray-200",
                  rclone.className,
                  isMobile && "shadow-none",
                )}
              >
                {rclone.text}
              </Badge>
            </TooltipWrapper>
            {file.rcloneStatus === "failed" && (
              <Button
                type="button"
                variant="ghost"
                size="xs"
                disabled={retrying}
                onClick={() => void retryUpload()}
                aria-label="Retry rclone upload"
              >
                <RotateCcw className={cn("h-3 w-3", retrying && "animate-spin")} />
              </Button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
