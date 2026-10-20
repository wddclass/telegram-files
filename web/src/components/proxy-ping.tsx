import useSWR from "swr";
import { ChevronsLeftRightEllipsis } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

export default function ProxyPing({ accountId }: { accountId: string }) {
  const t = useTranslations("proxy");
  const { data, isLoading, error, mutate } = useSWR<
    {
      ping: number;
    },
    Error
  >(`/telegram/${accountId}/ping`, {
    errorRetryCount: 2,
  });

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          <div
            className="flex items-center space-x-2 rounded bg-gray-100 p-1 dark:bg-gray-800"
            onClick={() => {
              if (isLoading) return;
              void mutate(undefined, true);
            }}
          >
            <ChevronsLeftRightEllipsis className="h-4 w-4 text-gray-500" />
            {isLoading && (
              <div className="h-6 w-14 animate-pulse rounded bg-gray-200 dark:bg-gray-700">
                {t("testingShort")}
              </div>
            )}
            {!isLoading && error && <span>{t("failed")}</span>}
            {!isLoading && data && (
              <div className="flex h-6 items-center space-x-2">
                <span className="text-sm">
                  {(data.ping * 1000).toFixed(0)} ms
                </span>
              </div>
            )}
          </div>
        </TooltipTrigger>
        <TooltipContent>
          {t("ping")}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
