import React from "react";
import useSWR from "swr";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  CheckCircle,
  Clock,
  CloudDownload,
  Download,
  File,
  FileText,
  Image,
  LineChart,
  LoaderPinwheel,
  Music,
  Network,
  PauseCircle,
  Upload,
  Video,
} from "lucide-react";
import { telegramApi, type TelegramApiArg } from "@/lib/api";
import { formatDistanceToNow } from "date-fns";
import { Button } from "@/components/ui/button";
import useSWRMutation from "swr/mutation";
import type { TelegramApiResult } from "@/lib/types"; // Define a fetcher function to handle the API request
import prettyBytes from "pretty-bytes";
import { useSettings } from "@/hooks/use-settings";
import { useTranslations } from "next-intl";
import { useDateFnsLocale } from "@/i18n/locale";

// Interface defining the structure of the data returned from the API
interface StatisticsData {
  total: number;
  downloading: number;
  paused: number;
  completed: number;
  error: number;
  photo: number;
  video: number;
  audio: number;
  file: number;
  networkStatistics: {
    sinceDate: number;
    sentBytes: number;
    receivedBytes: number;
  };
  speedStats: {
    interval: number;
    avgSpeed: number;
    maxSpeed: number;
    medianSpeed: number;
    minSpeed: number;
  };
}

// Props interface for the component, expecting a telegramId as input
interface FileStatisticsProps {
  telegramId: string;
}

const FileStatistics: React.FC<FileStatisticsProps> = ({ telegramId }) => {
  const { settings } = useSettings();
  const t = useTranslations("statistics");
  const dateLocale = useDateFnsLocale();
  // Use SWR for data fetching and caching
  const { data, error, mutate } = useSWR<StatisticsData, Error>(
    `/telegram/${telegramId}/download-statistics`,
  );

  const { trigger: triggerReset, isMutating: isResetMutating } = useSWRMutation<
    TelegramApiResult,
    Error,
    string,
    TelegramApiArg
  >("/telegram/api", telegramApi, {
    onSuccess: () => {
      void mutate();
    },
  });

  // Render an error message if the API call fails
  if (error) {
    return (
      <div className="flex items-center space-x-2 rounded-lg bg-white p-4 text-red-600 shadow-md dark:bg-red-50 dark:text-red-400">
        <AlertTriangle className="h-5 w-5" />
        <span>{t("loadFailed")}</span>
      </div>
    );
  }

  // Render a loading indicator while the data is being fetched
  if (!data) {
    return (
      <div className="flex items-center space-x-2 rounded-lg bg-white p-4 text-gray-600 shadow-md dark:bg-gray-50 dark:text-gray-400">
        <LoaderPinwheel
          className="h-5 w-5 animate-spin"
          style={{ strokeWidth: "0.8px" }}
        />
        <span>{t("loading")}</span>
      </div>
    );
  }

  // Destructure the fetched data for easier usage
  const {
    total,
    downloading,
    paused,
    completed,
    error: errorCount,
    photo,
    video,
    audio,
    file,
  } = data;

  // Prepare an array of completed file types with their respective icons
  const completedTypes = [
    {
      label: t("photo"),
      value: photo,
      // eslint-disable-next-line jsx-a11y/alt-text
      icon: <Image className="h-5 w-5 text-blue-500" />,
    },
    {
      label: t("video"),
      value: video,
      icon: <Video className="h-5 w-5 text-green-500" />,
    },
    {
      label: t("audio"),
      value: audio,
      icon: <Music className="h-5 w-5 text-purple-500" />,
    },
    {
      label: t("file"),
      value: file,
      icon: <File className="h-5 w-5 text-gray-500" />,
    },
  ];

  const avgStatFields = [
    {
      label: t("avg"),
      value: prettyBytes(data.speedStats.avgSpeed, { bits: settings?.speedUnits === 'bits' }) + "/s",
      icon: PauseCircle,
      color: "text-blue-500",
      bgColor: "bg-blue-100",
    },
    {
      label: t("max"),
      value: prettyBytes(data.speedStats.maxSpeed, { bits: settings?.speedUnits === 'bits' }) + "/s",
      icon: ArrowUp,
      color: "text-green-500",
      bgColor: "bg-green-100",
    },
    {
      label: t("median"),
      value: prettyBytes(data.speedStats.medianSpeed, { bits: settings?.speedUnits === 'bits' }) + "/s",
      icon: LineChart,
      color: "text-purple-500",
      bgColor: "bg-purple-100",
    },
    {
      label: t("min"),
      value: prettyBytes(data.speedStats.minSpeed, { bits: settings?.speedUnits === 'bits' }) + "/s",
      icon: ArrowDown,
      color: "text-red-500",
      bgColor: "bg-red-100",
    },
  ];

  return (
    <div className="space-y-6 rounded-lg bg-gray-50 p-2 dark:bg-gray-800 md:p-6">
      <div className="flex-1 rounded-lg bg-white p-4 shadow-md dark:bg-gray-900">
        <div className="flex items-center space-x-3 border-gray-200">
          <h3 className="text-md flex items-center space-x-2 font-semibold text-gray-700 dark:text-gray-200">
            <CloudDownload className="h-5 w-5 text-blue-600" />
            <span>{t("download")}</span>
          </h3>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-4 md:grid-cols-5">
          <div className="rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
            <div className="flex items-center space-x-2">
              <FileText className="h-5 w-5 text-gray-600 dark:text-gray-400" />
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {t("total")}
              </span>
            </div>
            <div className="mt-2 text-center text-lg font-semibold text-gray-800 dark:text-gray-200">
              {total}
            </div>
          </div>
          <div className="rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
            <div className="flex items-center space-x-2">
              <Download className="h-5 w-5 text-blue-600" />
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {t("downloading")}
              </span>
            </div>
            <div className="mt-2 text-center text-lg font-semibold text-gray-800 dark:text-gray-200">
              {downloading}
            </div>
          </div>
          <div className="rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
            <div className="flex items-center space-x-2">
              <PauseCircle className="h-5 w-5 text-yellow-500" />
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {t("paused")}
              </span>
            </div>
            <div className="mt-2 text-center text-lg font-semibold text-gray-800 dark:text-gray-200">
              {paused}
            </div>
          </div>
          <div className="rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
            <div className="flex items-center space-x-2">
              <CheckCircle className="h-5 w-5 text-green-600" />
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {t("completed")}
              </span>
            </div>
            <div className="mt-2 text-center text-lg font-semibold text-gray-800 dark:text-gray-200">
              {completed}
            </div>
          </div>
          <div className="rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
            <div className="flex items-center space-x-2">
              <AlertTriangle className="h-5 w-5 text-red-600" />
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {t("error")}
              </span>
            </div>
            <div className="mt-2 text-center text-lg font-semibold text-gray-800 dark:text-gray-200">
              {errorCount}
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 rounded-lg bg-white p-4 shadow-md dark:bg-gray-900">
        <div className="flex items-center space-x-3 border-gray-200 dark:border-gray-700">
          <h3 className="text-md flex items-center space-x-2 text-nowrap font-semibold text-gray-700 dark:text-gray-200">
            <Clock className="h-5 w-5 text-yellow-500" />
            <span>{t("speed")}</span>
            <span className="text-sm font-medium text-gray-500 dark:text-gray-400">
              {t("interval", { minutes: data.speedStats.interval / 60 })}
            </span>
          </h3>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-4 md:grid-cols-4">
          {avgStatFields.map((stat, index) => (
            <div
              key={index}
              className="flex flex-col space-y-3 rounded-xl border border-gray-100 p-4 transition-colors hover:border-gray-200 dark:border-gray-700 dark:hover:border-gray-600"
            >
              <div className="flex items-center space-x-2">
                <div className={`rounded-lg p-2 ${stat.bgColor}`}>
                  <stat.icon className={`h-5 w-5 ${stat.color}`} />
                </div>
                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">
                  {stat.label}
                </span>
              </div>
              <div className="text-lg font-semibold text-gray-800 dark:text-gray-200">
                {stat.value}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        <div className="flex-1 rounded-lg bg-white p-4 shadow-md dark:bg-gray-900 md:col-span-2">
          <h3 className="text-md flex items-center space-x-2 font-semibold text-gray-700 dark:text-gray-200">
            <CheckCircle className="h-5 w-5 text-green-600" />
            <span>{t("byType")}</span>
          </h3>
          <ul className="mt-4 grid grid-cols-2 gap-4">
            {completedTypes.map((type) => (
              <li
                key={type.label}
                className="rounded-lg bg-gray-50 p-3 shadow-sm dark:bg-gray-800"
              >
                <div className="flex items-center space-x-2">
                  {type.icon}
                  <span className="text-sm text-gray-600 dark:text-gray-400">
                    {type.label}
                  </span>
                </div>
                <div className="mt-2 text-lg font-semibold text-gray-800 dark:text-gray-200">
                  {type.value}
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex-1 rounded-lg bg-white p-4 shadow-md dark:bg-gray-900">
          <h3 className="text-md flex items-center space-x-2 font-semibold text-gray-700 dark:text-gray-200">
            <Network className="h-5 w-5 text-gray-500" />
            <span>{t("network")}</span>
          </h3>
          <div className="mt-4 h-full">
            <div className="flex items-center justify-center space-x-2 rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
              <Upload className="h-5 w-5 text-yellow-500" />
              <span className="text-lg font-semibold text-gray-800 dark:text-gray-200">
                {prettyBytes(data.networkStatistics.sentBytes)}
              </span>
            </div>
            <div className="mt-4 flex items-center justify-center space-x-2 rounded-lg bg-gray-50 p-4 shadow-sm dark:bg-gray-800">
              <Download className="h-5 w-5 text-blue-600" />
              <span className="text-lg font-semibold text-gray-800 dark:text-gray-200">
                {prettyBytes(data.networkStatistics.receivedBytes)}
              </span>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <Button
                variant="outline"
                size="sm"
                disabled={isResetMutating}
                onClick={() => {
                  void triggerReset({
                    data: {},
                    method: "ResetNetworkStatistics",
                  });
                }}
              >
                {isResetMutating ? t("resetting") : t("reset")}
              </Button>
              <p className="text-right text-sm text-gray-400">
                {t("since", {
                  time: formatDistanceToNow(
                    new Date(data.networkStatistics.sinceDate * 1000),
                    {
                      addSuffix: true,
                      locale: dateLocale,
                    },
                  ),
                })}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default FileStatistics;
