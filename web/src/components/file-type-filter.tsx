import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { FileType } from "@/lib/types";
import useSWR from "swr";
import { Ellipsis } from "lucide-react";
import { Label } from "@/components/ui/label";
import * as React from "react";
import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { isFileTypeKey } from "@/i18n/labels";

interface FileTypeFilterProps {
  offline: boolean;
  telegramId: string;
  chatId: string;
  type: FileType | "all";
  onChange: (type: FileType | "all") => void;
}

// 把子组件移到外部
interface FileTypeSelectItemProps {
  value: FileType;
  counts?: Record<FileType, number>;
  isLoading: boolean;
}

const FileTypeSelectItem = ({
  value,
  counts,
  isLoading,
}: FileTypeSelectItemProps) => {
  const t = useTranslations("fileType");
  return (
    <SelectItem value={value}>
      <div className="flex items-center gap-5">
        <span className="w-10">
          {isFileTypeKey(value) ? t(value) : value}
        </span>
        {isLoading ? (
          <Ellipsis className="h-4 w-4 animate-pulse" />
        ) : (
          <span className="text-xs text-gray-400">
            {counts?.[value] ? `(${counts[value]})` : "(0)"}
          </span>
        )}
      </div>
    </SelectItem>
  );
};

export default function FileTypeFilter({
  offline,
  telegramId,
  chatId,
  type,
  onChange,
}: FileTypeFilterProps) {
  const [localType, setLocalType] = React.useState<FileType | "all">(type);
  const t = useTranslations("filters");
  const tType = useTranslations("fileType");
  const { data: counts, isLoading } = useSWR<Record<FileType, number>>(
    `/telegram/${telegramId}/chat/${chatId}/files/count?offline=${offline}`,
  );

  const handleTypeChange = (value: FileType | "all") => {
    setLocalType(value);
    onChange(value);
  };

  useEffect(() => {
    if (!offline && localType === "all") {
      setLocalType("media");
      onChange("media");
    }
  }, [localType, offline, onChange]);

  return (
    <div className="space-y-2">
      <Label>{t("type")}</Label>
      <Select value={localType} onValueChange={handleTypeChange}>
        <SelectTrigger>
          <SelectValue placeholder={t("typePlaceholder")} />
        </SelectTrigger>
        <SelectContent>
          {offline && <SelectItem value="all">{tType("all")}</SelectItem>}
          <FileTypeSelectItem
            value="media"
            counts={counts}
            isLoading={isLoading}
          />
          <FileTypeSelectItem
            value="photo"
            counts={counts}
            isLoading={isLoading}
          />
          <FileTypeSelectItem
            value="video"
            counts={counts}
            isLoading={isLoading}
          />
          <FileTypeSelectItem
            value="audio"
            counts={counts}
            isLoading={isLoading}
          />
          <FileTypeSelectItem
            value="file"
            counts={counts}
            isLoading={isLoading}
          />
        </SelectContent>
      </Select>
    </div>
  );
}
