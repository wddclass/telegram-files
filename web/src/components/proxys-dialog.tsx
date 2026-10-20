import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { EarthLock } from "lucide-react";
import Proxys, { type ProxysProps } from "@/components/proxys";
import { VisuallyHidden } from "@radix-ui/react-visually-hidden";
import React, { useMemo } from "react";
import { cn } from "@/lib/utils";
import useSWR from "swr";
import { TooltipWrapper } from "@/components/ui/tooltip";
import { useTranslations } from "next-intl";

type ProxysDialogProps = {
  className?: string;
} & ProxysProps;

export default function ProxysDialog({
  className,
  telegramId,
  ...proxyProps
}: ProxysDialogProps) {
  const t = useTranslations("proxy");
  const { data, isLoading } = useSWR<{
    success: boolean;
  }>(telegramId ? `/telegram/${telegramId}/test-network` : undefined);

  const triggerTooltipContent = useMemo(() => {
    if (!telegramId) {
      return t("canAdd");
    }
    return (
      <>
        {isLoading && t("testing")}
        {!isLoading && data?.success && t("connected")}
        {!isLoading && !data?.success && t("failedConnect")}
      </>
    );
  }, [data, isLoading, telegramId, t]);

  const triggerClassName = telegramId
    ? cn(
        isLoading &&
          "animate-pulse border-4 border-blue-400 text-blue-400 dark:border-blue-500 dark:text-blue-500",
        !isLoading &&
          data?.success &&
          "border-4 border-green-400 text-green-400 dark:border-green-500 dark:text-green-500",
        !isLoading &&
          !data?.success &&
          "border-4 border-red-400 text-red-400 dark:border-red-500 dark:text-red-500",
      )
    : "";

  return (
    <Dialog>
      <TooltipWrapper content={triggerTooltipContent}>
        <DialogTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className={cn(
              "rounded-full bg-primary px-2 text-accent hover:bg-primary/90 hover:text-accent",
              triggerClassName,
              className,
            )}
          >
            <EarthLock className="h-4 w-4" />
          </Button>
        </DialogTrigger>
      </TooltipWrapper>
      <DialogContent
        className="md:max-w-2/3 h-full w-full max-w-full md:h-3/4 md:w-2/3"
        aria-describedby={undefined}
      >
        <VisuallyHidden>
          <DialogTitle>{t("title")}</DialogTitle>
        </VisuallyHidden>
        <div className="mt-3">
          <Proxys telegramId={telegramId} {...proxyProps} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
