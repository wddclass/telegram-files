import type { TelegramFile } from "@/lib/types";
import { useFileSpeed } from "@/hooks/use-file-speed";
import {
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
} from "@/components/ui/drawer";
import FileStatus from "@/components/file-status";
import SpoiledWrapper from "@/components/spoiled-wrapper";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";
import {
  Calendar,
  Clock10,
  ClockArrowDown,
  Download,
  HardDrive,
  Type,
  View,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import prettyBytes from "pretty-bytes";
import { formatDistanceToNow } from "date-fns";
import { useTranslations } from "next-intl";
import { useDateFnsLocale } from "@/i18n/locale";
import { isFileTypeKey } from "@/i18n/labels";
import { Button } from "@/components/ui/button";
import { MobileFileControl } from "@/components/file-control";
import React from "react";
import FileImage from "../file-image";
import { MobileFileTags } from "@/components/file-tags";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";

export default function FileInfo({
  file,
  onView,
  onFileTagsClick,
}: {
  file: TelegramFile;
  onView: () => void;
  onFileTagsClick: (file: TelegramFile) => void;
}) {
  const { downloadProgress } = useFileSpeed(file);
  const t = useTranslations("time");
  const tFiles = useTranslations("files");
  const tCol = useTranslations("columns");
  const tType = useTranslations("fileType");
  const dateLocale = useDateFnsLocale();
  return (
    <>
      <DrawerHeader className="text-center">
        <div className="flex flex-col items-center justify-center gap-1">
          <FileImage file={file} className="" isGalleryLayout />
          <span className="max-w-64 truncate">{file.fileName}</span>
        </div>
        <div className="py-1">
          <FileStatus file={file} />
        </div>
        {file.caption && (
          <SpoiledWrapper hasSensitiveContent={file.hasSensitiveContent}>
            <DrawerDescription
              className="mx-auto mt-2 max-h-36 max-w-md overflow-y-auto text-start"
              dangerouslySetInnerHTML={{
                __html: file.caption.replaceAll("\n", "<br />"),
              }}
            ></DrawerDescription>
          </SpoiledWrapper>
        )}
        {downloadProgress > 0 && downloadProgress !== 100 && (
          <div className="flex items-end justify-between gap-2">
            <Progress
              value={downloadProgress}
              className="flex-1 rounded-none md:w-32"
            />
          </div>
        )}
      </DrawerHeader>

      <div className="px-2 pb-1">
        <Separator />
      </div>

      <Accordion type="single" collapsible>
        <AccordionItem value="file-info" className="border-none">
          <AccordionTrigger className="px-4 py-1 text-sm font-semibold hover:no-underline">
            <div className="col-span-2">
              {file.loaded && (
                <MobileFileTags
                  className="max-w-fit"
                  tags={file.tags}
                  onClick={() => onFileTagsClick(file)}
                />
              )}
            </div>
          </AccordionTrigger>
          <AccordionContent className="max-h-40 space-y-6 overflow-y-auto px-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Type className="h-4 w-4" />
                  <span>{tCol("type")}</span>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {isFileTypeKey(file.type) ? tType(file.type) : file.type}
                </Badge>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <HardDrive className="h-4 w-4" />
                  <span>{tCol("size")}</span>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {prettyBytes(file.size)}
                </Badge>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Calendar className="h-4 w-4" />
                  <span>{t("messageReceived")}</span>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {formatDistanceToNow(new Date(file.date * 1000), {
                    addSuffix: true,
                    locale: dateLocale,
                  })}
                </Badge>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Download className="h-4 w-4" />
                  <span>{t("downloadedSize")}</span>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {prettyBytes(file.downloadedSize)}
                </Badge>
              </div>

              {file.downloadStatus !== "idle" && file.startDate !== 0 && (
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Clock10 className="h-4 w-4" />
                    <span>{t("downloadAt")}</span>
                  </div>
                  <Badge variant="secondary" className="text-xs">
                    {formatDistanceToNow(new Date(file.startDate), {
                      addSuffix: true,
                      locale: dateLocale,
                    })}
                  </Badge>
                </div>
              )}

              {file.completionDate && (
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <ClockArrowDown className="h-4 w-4" />
                    <span>{t("completionAt")}</span>
                  </div>
                  <Badge variant="secondary" className="text-xs">
                    {formatDistanceToNow(new Date(file.completionDate), {
                      addSuffix: true,
                      locale: dateLocale,
                    })}
                  </Badge>
                </div>
              )}
            </div>
          </AccordionContent>
        </AccordionItem>
      </Accordion>

      <DrawerFooter>
        {file.downloadStatus === "completed" &&
          (file.type === "video" || file.type === "photo") && (
            <Button className="w-full" onClick={onView}>
              <View className="h-4 w-4" />
              <span>{tFiles("view")}</span>
            </Button>
          )}
        <MobileFileControl file={file} />
      </DrawerFooter>
    </>
  );
}
