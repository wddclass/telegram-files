import { Archive, Check, ChevronsUpDown, Ellipsis, List } from "lucide-react";
import { Button } from "./ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "./ui/command";
import { useEffect, useState } from "react";
import { useTelegramChat } from "@/hooks/use-telegram-chat";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { CommandLoading } from "cmdk";
import { Toggle } from "./ui/toggle";
import { TooltipWrapper } from "@/components/ui/tooltip";
import { useTranslations } from "next-intl";
import { chatTypeKey } from "@/i18n/labels";

export default function ChatSelect({ disabled }: { disabled: boolean }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [archived, setArchived] = useState(false);
  const t = useTranslations("chats");
  const {
    isLoading,
    handleQueryChange,
    chats,
    chat: selectedChat,
    handleChatChange,
    handleArchivedChange,
  } = useTelegramChat();

  useEffect(() => {
    handleQueryChange(search);
  }, [search, handleQueryChange]);

  useEffect(() => {
    handleArchivedChange(archived);
  }, [archived, handleArchivedChange]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={disabled}>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between md:w-[250px]"
        >
          {selectedChat ? (
            <div className="flex items-center gap-2">
              <Avatar className="h-6 w-6">
                <AvatarImage
                  src={`data:image/png;base64,${selectedChat.avatar}`}
                />
                <AvatarFallback>
                  {" "}
                  {selectedChat.name[0] ?? selectedChat.id[0]}
                </AvatarFallback>
              </Avatar>
              <span className="max-w-[170px] overflow-hidden truncate">
                {(selectedChat.name ?? "").length > 0
                  ? selectedChat.name
                  : selectedChat.id}
              </span>
            </div>
          ) : (
            t("select")
          )}
          <ChevronsUpDown className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[300px] p-0" modal={true}>
        <Command shouldFilter={false}>
          <div className="flex w-full border-b">
            <TooltipWrapper
              content={archived ? t("showMain") : t("showArchived")}
            >
              <Toggle
                className="h-9 rounded-none border-r"
                pressed={archived}
                onPressedChange={setArchived}
              >
                {archived ? (
                  <Archive className="h-5 w-5" />
                ) : (
                  <List className="h-5 w-5" />
                )}
              </Toggle>
            </TooltipWrapper>
            <div className="flex-1">
              <CommandInput
                placeholder={t("search")}
                className="h-9"
                value={search}
                onValueChange={setSearch}
              />
            </div>
          </div>
          <CommandList className="relative">
            {isLoading && (
              <CommandLoading>
                <div className="absolute left-1/2 top-1/2 -translate-x-2 -translate-y-2 transform">
                  <Ellipsis className="h-4 w-4 animate-pulse" />
                </div>
              </CommandLoading>
            )}
            <CommandEmpty>
              {!isLoading && chats.length === 0 && t("empty")}
            </CommandEmpty>
            <CommandGroup>
              {chats.map((chat) => (
                <CommandItem
                  key={chat.id}
                  value={chat.id}
                  onSelect={(currentValue) => {
                    handleChatChange(currentValue);
                    setOpen(false);
                  }}
                >
                  <div className="flex items-center gap-2">
                    <div
                      className={cn(
                        "h-3 w-1 rounded-md bg-green-500 opacity-0",
                        {
                          "opacity-100":
                            chat.auto?.download.enabled ??
                            chat.auto?.preload.enabled ??
                            chat.auto?.transfer.enabled,
                        },
                      )}
                    />
                    <Avatar className="h-6 w-6">
                      <AvatarImage
                        src={`data:image/png;base64,${chat.avatar}`}
                      />
                      <AvatarFallback>
                        {chat.name[0] ?? chat.id[0]}
                      </AvatarFallback>
                    </Avatar>
                    <div className="flex flex-col">
                      <span className="font-medium">
                        {(chat.name ?? "").length > 0 ? chat.name : chat.id}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {(chatTypeKey(chat.type)
                          ? t(`types.${chatTypeKey(chat.type)!}`)
                          : chat.type)}{" "}
                        • {t("unread", { count: chat.unreadCount ?? 0 })}
                      </span>
                    </div>
                  </div>
                  <Check
                    className={cn(
                      "ml-auto",
                      selectedChat?.id === chat.id
                        ? "opacity-100"
                        : "opacity-0",
                    )}
                  />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
