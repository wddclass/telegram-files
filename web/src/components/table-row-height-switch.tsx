"use client";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useLocalStorage } from "@/hooks/use-local-storage";
import { Rows2, Rows3, Rows4 } from "lucide-react";
import { useTranslations } from "next-intl";

const heightOptions = [
  {
    id: "s",
    label: "Small",
    value: "h-12",
    px: 48,
    icon: <Rows4 className="h-4 w-4" />,
  },
  {
    id: "m",
    label: "Medium",
    value: "h-24",
    px: 96,
    icon: <Rows3 className="h-4 w-4" />,
  },
  {
    id: "l",
    label: "Large",
    value: "h-64",
    px: 256,
    icon: <Rows2 className="h-4 w-4" />,
  },
] as const;

export type RowHeight = (typeof heightOptions)[number]["id"];

export const getRowHeightTailwindClass = (rowHeight: RowHeight | undefined) =>
  heightOptions.find((h) => h.id === rowHeight)?.value ??
  heightOptions[1].value;

export const getRowHeightPX = (rowHeight: RowHeight | undefined) =>
  heightOptions.find((h) => h.id === rowHeight)?.px ?? heightOptions[1].px;

export function useRowHeightLocalStorage(
  tableName: string,
  defaultValue: RowHeight,
) {
  const [rowHeight, setRowHeight, clearRowHeight] = useLocalStorage<RowHeight>(
    `${tableName}Height`,
    defaultValue,
  );

  return [rowHeight, setRowHeight, clearRowHeight] as const;
}

export const TableRowHeightSwitch = ({
  rowHeight,
  setRowHeightAction,
}: {
  rowHeight: RowHeight;
  setRowHeightAction: (e: RowHeight) => void;
}) => {
  const t = useTranslations("rowHeight");
  const labels = { s: t("small"), m: t("medium"), l: t("large") };
  return (
    <Tabs
      value={rowHeight}
      onValueChange={(e) => {
        setRowHeightAction(e as "s" | "m" | "l");
      }}
      key="height"
    >
      <TabsList className="gap-1 border bg-transparent px-2">
        {heightOptions.map(({ id, icon }) => (
          <TabsTrigger
            key={id}
            value={id}
            className="px-2 shadow-none data-[state=active]:bg-input data-[state=active]:ring-border"
          >
            <span role="img" aria-label={labels[id]}>
              {icon}
            </span>
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
};
