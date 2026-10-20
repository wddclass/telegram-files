import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  type Auto,
  type AutoDownloadRule,
  type AutoTransferRule,
  DuplicationPolicies,
  type DuplicationPolicy,
  type FileType,
  TransferPolices,
  type TransferPolicy,
} from "@/lib/types";
import React, { useId, useState } from "react";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Check, ChevronsUpDown, X } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import {
  Command,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { useMutationObserver } from "@/hooks/use-mutation-observer";
import { cn } from "@/lib/utils";
import { Textarea } from "@/components/ui/textarea";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { isFileTypeKey } from "@/i18n/labels";

interface AutomationFormProps {
  auto: Auto;
  onChange: (auto: Auto) => void;
}

export default function AutomationForm({
  auto,
  onChange,
}: AutomationFormProps) {
  const t = useTranslations("automation");
  return (
    <div className="space-y-4">
      <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <Label htmlFor="enable-preload">{t("enablePreload")}</Label>
          <Switch
            id="enable-preload"
            checked={auto.preload.enabled}
            onCheckedChange={(checked) => {
              onChange({
                ...auto,
                preload: {
                  ...auto.preload,
                  enabled: checked,
                },
              });
            }}
          />
        </div>
        {auto.preload.enabled && (
          <div className="space-y-4 rounded-md border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800">
            <div className="flex items-start">
              <span className="mr-3 mt-1.5 h-3 w-2 flex-shrink-0 rounded-full bg-cyan-400"></span>
              <p className="text-sm leading-6 text-gray-700 dark:text-gray-300">
                {t("preloadHint")}
              </p>
            </div>
          </div>
        )}
      </div>
      <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <Label htmlFor="enable-auto-download">{t("enableDownload")}</Label>
          <Switch
            id="enable-auto-download"
            checked={auto.download.enabled}
            onCheckedChange={(checked) => {
              onChange({
                ...auto,
                download: {
                  ...auto.download,
                  enabled: checked,
                },
              });
            }}
          />
        </div>
        {auto.download.enabled && (
          <>
            <div className="space-y-4 rounded-md border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800">
              <div className="flex items-start">
                <span className="mr-3 mt-1.5 h-3 w-2 flex-shrink-0 rounded-full bg-cyan-400"></span>
                <p className="text-sm leading-6 text-gray-700 dark:text-gray-300">
                  {t("downloadHint")}
                </p>
              </div>
              <div className="flex items-start">
                <span className="mr-3 mt-1.5 h-3 w-2 flex-shrink-0 rounded-full bg-cyan-400"></span>
                <p className="text-sm leading-6 text-gray-700 dark:text-gray-300">
                  {t("historyHint")}
                </p>
              </div>
              <div className="flex items-start">
                <span className="mr-3 mt-1.5 h-3 w-2 flex-shrink-0 rounded-full bg-cyan-400"></span>
                <p className="text-sm leading-6 text-gray-700 dark:text-gray-300">
                  {t("order")}
                  <span className="ml-1 rounded bg-blue-100 px-2 text-blue-700 dark:bg-blue-800 dark:text-blue-200">
                    {t("orderValue")}
                  </span>
                </p>
              </div>
            </div>
            <DownloadRule
              value={auto.download.rule}
              onChange={(value) => {
                onChange({
                  ...auto,
                  download: {
                    ...auto.download,
                    rule: value,
                  },
                });
              }}
            />
          </>
        )}
      </div>
      <div className="space-y-4 rounded-md border border-gray-200 p-4 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <Label htmlFor="enable-transfer">{t("enableTransfer")}</Label>
          <Switch
            id="enable-transfer"
            checked={auto.transfer.enabled}
            onCheckedChange={(checked) => {
              onChange({
                ...auto,
                transfer: {
                  ...auto.transfer,
                  enabled: checked,
                },
              });
            }}
          />
        </div>
        {auto.transfer.enabled && (
          <>
            <div className="space-y-4 rounded-md border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800">
              <div className="flex items-start">
                <span className="mr-3 mt-1.5 h-3 w-2 flex-shrink-0 rounded-full bg-cyan-400"></span>
                <p className="text-sm leading-6 text-gray-700 dark:text-gray-300">
                  {t("transferHint")}
                </p>
              </div>
            </div>
            <TransferRule
              value={auto.transfer.rule}
              onChange={(value) => {
                onChange({
                  ...auto,
                  transfer: {
                    ...auto.transfer,
                    rule: value,
                  },
                });
              }}
            />
          </>
        )}
      </div>
    </div>
  );
}

interface DownloadRuleProps {
  value: AutoDownloadRule;
  onChange: (value: AutoDownloadRule) => void;
}

function DownloadRule({ value, onChange }: DownloadRuleProps) {
  const t = useTranslations("automation");
  const tType = useTranslations("fileType");
  const handleQueryChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({
      ...value,
      query: e.target.value,
    });
  };

  const handleFilterExprChange = (
    e: React.ChangeEvent<HTMLTextAreaElement>,
  ) => {
    onChange({
      ...value,
      filterExpr: e.target.value,
    });
  };

  const handleFileTypeSelect = (type: string) => {
    if (value.fileTypes.includes(type as Exclude<FileType, "media">)) {
      return;
    }

    onChange({
      ...value,
      fileTypes: [...value.fileTypes, type as Exclude<FileType, "media">],
    });
  };

  const removeFileType = (typeToRemove: string) => {
    onChange({
      ...value,
      fileTypes: value.fileTypes.filter((type) => type !== typeToRemove),
    });
  };

  return (
    <Accordion type="single" collapsible>
      <AccordionItem value="advanced">
        <AccordionTrigger className="hover:no-underline">
          {t("advanced")}
        </AccordionTrigger>
        <AccordionContent>
          <div className="flex flex-col space-y-4 rounded-md border p-4 shadow">
            <div className="flex flex-col space-y-2">
              <Label htmlFor="query-keyword">{t("query")}</Label>
              <Input
                id="query-keyword"
                type="text"
                className="w-full"
                placeholder={t("queryPlaceholder")}
                value={value.query}
                onChange={handleQueryChange}
              />
            </div>
            <div className="flex flex-col space-y-2">
              <Label htmlFor="filter-expr">
                {t("filterExpr")}
                <Link
                  href="https://github.com/jarvis2f/telegram-files/blob/main/misc/filter-expression-guide.md"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ml-2 text-sm text-blue-600 hover:underline"
                >
                  {t("learnMore")}
                </Link>
              </Label>
              <Textarea
                id="filter-expr"
                className="w-full"
                placeholder={t("filterPlaceholder")}
                value={value.filterExpr}
                onChange={handleFilterExprChange}
              />
            </div>

            <div className="flex flex-col space-y-2">
              <Label htmlFor="fileTypes">{t("fileTypes")}</Label>
              <Select onValueChange={handleFileTypeSelect}>
                <SelectTrigger id="fileTypes">
                  <SelectValue placeholder={t("selectFileTypes")} />
                </SelectTrigger>
                <SelectContent>
                  {["photo", "video", "audio", "file"].map((type) => (
                    <SelectItem key={type} value={type}>
                      {isFileTypeKey(type) ? tType(type) : type}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <div className="mt-2 flex flex-wrap gap-2">
                {value.fileTypes.map((type) => (
                  <Badge
                    key={type}
                    className="flex items-center gap-1 px-2 py-1"
                    variant="secondary"
                  >
                    {isFileTypeKey(type) ? tType(type) : type}
                    <X
                      className="h-3 w-3 cursor-pointer"
                      onClick={() => removeFileType(type)}
                    />
                  </Badge>
                ))}
              </div>
            </div>

            <div className="rounded-md border p-4">
              <div className="flex items-center justify-between">
                <Label htmlFor="download-history">{t("downloadHistory")}</Label>
                <Switch
                  id="download-history"
                  checked={value.downloadHistory}
                  onCheckedChange={(checked) =>
                    onChange({
                      ...value,
                      downloadHistory: checked,
                    })
                  }
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {t("downloadHistoryHint")}
              </p>
            </div>
            <div className="rounded-md border p-4">
              <div className="flex items-center justify-between">
                <Label htmlFor="download-comment-files">
                  {t("downloadCommentFiles")}
                </Label>
                <Switch
                  id="download-comment-files"
                  checked={value.downloadCommentFiles}
                  onCheckedChange={(checked) =>
                    onChange({
                      ...value,
                      downloadCommentFiles: checked,
                    })
                  }
                />
              </div>
            </div>
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

interface TransferRuleProps {
  value: AutoTransferRule;
  onChange: (value: AutoTransferRule) => void;
}

function TransferRule({ value, onChange }: TransferRuleProps) {
  const t = useTranslations("automation");
  const captionNameId = useId();
  const handleTransferRuleChange = (changes: Partial<AutoTransferRule>) => {
    onChange({
      ...value,
      ...changes,
    });
  };

  return (
    <Accordion type="single" collapsible>
      <AccordionItem value="advanced">
        <AccordionTrigger className="hover:no-underline">
          {t("advanced")}
        </AccordionTrigger>
        <AccordionContent>
          <div className="flex flex-col space-y-4 rounded-md border p-4 shadow">
            <div className="flex flex-col space-y-2">
              <Label htmlFor="destination">
                {t("destinationLabel")}
              </Label>
              <Input
                id="destination"
                type="text"
                className="w-full"
                placeholder={t("destinationPlaceholder")}
                value={value.destination}
                onChange={(e) => {
                  handleTransferRuleChange({ destination: e.target.value });
                }}
              />
            </div>

            <div className="flex flex-col space-y-2">
              <Label htmlFor="transfer-policy">{t("transferPolicy")}</Label>
              <PolicySelect
                policyType="transfer"
                value={value.transferPolicy}
                onChange={(policy) =>
                  handleTransferRuleChange({
                    transferPolicy: policy as TransferPolicy,
                  })
                }
              />
            </div>

            {value.transferPolicy === "GROUP_BY_AI" && (
              <div className="flex flex-col space-y-2">
                <Label htmlFor="prompt-template">
                  {t("prompt")}
                </Label>
                <Textarea
                  id="prompt-template"
                  className="w-full"
                  rows={4}
                  placeholder={t("promptPlaceholder")}
                  value={value.extra.promptTemplate || ""}
                  onChange={(e) =>
                    handleTransferRuleChange({
                      extra: {
                        ...value.extra,
                        promptTemplate: e.target.value,
                      },
                    })
                  }
                />
              </div>
            )}

            <div className="flex flex-col space-y-2">
              <Label htmlFor="duplication-policy">{t("duplicationPolicy")}</Label>
              <PolicySelect
                policyType="duplication"
                value={value.duplicationPolicy}
                onChange={(policy) =>
                  handleTransferRuleChange({
                    duplicationPolicy: policy as DuplicationPolicy,
                  })
                }
              />
            </div>

            <div className="rounded-md border p-4">
              <div className="flex items-center justify-between">
                <Label htmlFor="transfer-history">{t("transferHistory")}</Label>
                <Switch
                  id="transfer-history"
                  checked={value.transferHistory}
                  onCheckedChange={(checked) =>
                    handleTransferRuleChange({ transferHistory: checked })
                  }
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {t("transferHistoryHint")}
              </p>
            </div>

            <div className="rounded-md border p-4">
              <div className="flex items-center justify-between">
                <Label htmlFor={captionNameId}>{t("captionName")}</Label>
                <Switch
                  id={captionNameId}
                  checked={value.useCaptionName ?? false}
                  onCheckedChange={(checked) =>
                    handleTransferRuleChange({ useCaptionName: checked })
                  }
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {t("captionNameHint")}
              </p>
            </div>
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

type PolicyKey = TransferPolicy | DuplicationPolicy;

function policyTitle(
  t: ReturnType<typeof useTranslations<"automation">>,
  policy: string,
) {
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
}

function PolicyDescription({ policy }: { policy: PolicyKey }) {
  const t = useTranslations("automation");
  const example = (
    <p className="text-xs text-muted-foreground">{t("example")}</p>
  );
  const sample = (text: string) => (
    <p className="inline-block rounded bg-gray-100 p-1 text-xs text-muted-foreground dark:bg-gray-800 dark:text-gray-300">
      {text}
    </p>
  );

  if (policy === "GROUP_BY_CHAT") {
    return (
      <div className="space-y-2">
        <p className="text-sm">{t("policies.GROUP_BY_CHAT.description")}</p>
        {example}
        {sample(t("policies.GROUP_BY_CHAT.path"))}
      </div>
    );
  }
  if (policy === "GROUP_BY_TYPE") {
    return (
      <div className="space-y-2">
        <p className="text-sm">{t("policies.GROUP_BY_TYPE.description")}</p>
        {example}
        {sample(t("policies.GROUP_BY_TYPE.path"))}
      </div>
    );
  }
  if (policy === "GROUP_BY_AI") {
    return (
      <div className="space-y-2">
        <p className="text-sm">{t("policies.GROUP_BY_AI.description")}</p>
        <p className="text-sm">{t("policies.GROUP_BY_AI.promptHint")}</p>
        <p className="inline-block whitespace-pre-line rounded bg-gray-100 p-1 text-xs text-muted-foreground dark:bg-gray-800 dark:text-gray-300">
          {t("aiExample")}
        </p>
        <p className="text-sm">{t("aiFieldHint")}</p>
      </div>
    );
  }
  if (policy === "DIRECT") {
    return (
      <p className="text-sm text-muted-foreground">
        {t("policies.DIRECT.description")}
      </p>
    );
  }
  if (policy === "OVERWRITE") {
    return (
      <p className="text-sm text-muted-foreground">
        {t("policies.OVERWRITE.description")}
      </p>
    );
  }
  if (policy === "SKIP") {
    return (
      <p className="text-sm text-muted-foreground">
        {t("policies.SKIP.description")}
      </p>
    );
  }
  if (policy === "RENAME") {
    return (
      <p className="text-sm text-muted-foreground">
        {t("policies.RENAME.description")}
      </p>
    );
  }
  return (
    <p className="text-sm text-muted-foreground">
      {t("policies.HASH.description")}
    </p>
  );
}

interface PolicySelectProps {
  policyType: "transfer" | "duplication";
  value?: string;
  onChange: (value: string) => void;
}

function PolicySelect({ policyType, value, onChange }: PolicySelectProps) {
  const t = useTranslations("automation");
  const [open, setOpen] = useState(false);
  const polices =
    policyType === "transfer" ? TransferPolices : DuplicationPolicies;
  const [peekedPolicy, setPeekedPolicy] = useState<string>(value ?? polices[0]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-label={t("selectPolicy")}
          className="w-full justify-between"
        >
          {value ? policyTitle(t, value) : t("selectPolicyPlaceholder")}
          <ChevronsUpDown className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[250px] p-0" modal={true}>
        <HoverCard>
          <HoverCardContent
            side="top"
            align="start"
            forceMount
            className="min-h-[150px] w-auto min-w-64 max-w-[380px]"
          >
            <div className="grid gap-2">
              <h4 className="font-medium leading-none">
                {policyTitle(t, peekedPolicy)}
              </h4>
              <PolicyDescription policy={peekedPolicy as PolicyKey} />
            </div>
          </HoverCardContent>
          <Command>
            <CommandList className="h-[var(--cmdk-list-height)] max-h-[400px]">
              <HoverCardTrigger />
              <CommandGroup>
                {polices.map((policy) => (
                  <PolicyItem
                    key={policy}
                    policy={policy ?? ""}
                    isSelected={value === policy}
                    onPeek={setPeekedPolicy}
                    onSelect={() => {
                      onChange(policy);
                      setOpen(false);
                    }}
                  />
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </HoverCard>
      </PopoverContent>
    </Popover>
  );
}

interface PolicyItemProps {
  policy: string;
  isSelected: boolean;
  onSelect: () => void;
  onPeek: (policy: string) => void;
}

function PolicyItem({ policy, isSelected, onSelect, onPeek }: PolicyItemProps) {
  const t = useTranslations("automation");
  const ref = React.useRef<HTMLDivElement>(null);

  useMutationObserver(ref, (mutations) => {
    mutations.forEach((mutation) => {
      if (
        mutation.type === "attributes" &&
        mutation.attributeName === "aria-selected" &&
        ref.current?.getAttribute("aria-selected") === "true"
      ) {
        onPeek(policy);
      }
    });
  });

  return (
    <CommandItem
      key={policy}
      onSelect={onSelect}
      ref={ref}
      className="data-[selected=true]:bg-primary data-[selected=true]:text-primary-foreground"
    >
      {policyTitle(t, policy)}
      <Check
        className={cn("ml-auto", isSelected ? "opacity-100" : "opacity-0")}
      />
    </CommandItem>
  );
}
