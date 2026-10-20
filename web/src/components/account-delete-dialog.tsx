"use client";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Trash } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import useSWRMutation from "swr/mutation";
import { POST } from "@/lib/api";
import { useState } from "react";
import { useSWRConfig } from "swr";
import { useTelegramAccount } from "@/hooks/use-telegram-account";
import { useDebounce } from "use-debounce";
import { useTranslations } from "next-intl";

export default function AccountDeleteDialog({
  telegramId,
  className,
}: {
  telegramId: string;
  className?: string;
}) {
  const { toast } = useToast();
  const t = useTranslations("accounts");
  const tCommon = useTranslations("common");
  const [open, setOpen] = useState(false);
  const { mutate } = useSWRConfig();
  const { resetAccount } = useTelegramAccount();
  const { trigger: triggerDelete, isMutating: isDeleteMutating } =
    useSWRMutation(`/telegram/${telegramId}/delete`, POST, {
      onSuccess: () => {
        toast({ variant: "success", title: t("deleted") });
        resetAccount();
        // 延迟关闭对话框
        setTimeout(() => {
          void mutate("/telegrams");
          setOpen(false);
        }, 1000);
      },
    });

  const [debounceIsDeleteMutating] = useDebounce(isDeleteMutating, 500, {
    leading: true,
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        className={className}
        asChild
        onClick={(e) => {
          e.stopPropagation();
          setOpen(!open);
        }}
      >
        <Button variant="ghost" size="sm">
          <Trash className="h-4 w-4" />
        </Button>
      </DialogTrigger>
      <DialogContent
        aria-describedby={undefined}
        onPointerDownOutside={() => setOpen(false)}
        onClick={(e) => e.stopPropagation()}
      >
        <DialogHeader>
          <DialogTitle>{t("deleteTitle")}</DialogTitle>
        </DialogHeader>
        <DialogDescription>
          {t("deleteDescription")}
        </DialogDescription>
        <div className="flex justify-end gap-2">
          <Button
            variant="destructive"
            onClick={() => triggerDelete()}
            disabled={debounceIsDeleteMutating}
          >
            {debounceIsDeleteMutating ? tCommon("deleting") : tCommon("delete")}
          </Button>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={debounceIsDeleteMutating}
          >
            {tCommon("cancel")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
