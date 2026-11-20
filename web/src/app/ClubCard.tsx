import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CheckIcon, ChevronsUpDownIcon, SettingsIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { AuthMeResponse, BranchDetailResponse } from "@/api/generated/contracts";
import { useSwitchBranch } from "@/account/useSwitchBranch";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { myBranchesQuery } from "@/query/queries";
import { monogramOf } from "./monogram";

/** Как раскрывается список филиалов: выпадающим меню (компьютер) или шторкой снизу (телефон). */
export type ClubCardVariant = "menu" | "sheet";

/**
 * Карточка клуба сотрудника [me]: монограмма, название организации и текущий филиал.
 * При двух доступных филиалах и больше карточка — кнопка смены филиала, список раскрывается
 * способом [variant]; при одном филиале и пока список грузится — неинтерактивная подпись.
 */
export function ClubCard({
  api,
  me,
  variant,
  className,
}: {
  api: ApiClient;
  me: AuthMeResponse;
  variant: ClubCardVariant;
  className?: string;
}) {
  const { data } = useQuery(myBranchesQuery(api));
  const branches = data?.branches ?? [];
  if (branches.length < 2) {
    return (
      <div
        className={cn("flex min-w-0 items-center gap-2.5 rounded-lg bg-muted/60 p-2", className)}
      >
        <CardFace me={me} />
      </div>
    );
  }
  switch (variant) {
    case "menu":
      return <BranchMenu api={api} me={me} branches={branches} className={className} />;
    case "sheet":
      return <BranchSheet api={api} me={me} branches={branches} className={className} />;
  }
}

/** Классы карточки-кнопки: рамка, тень и видимый фокус. */
const BUTTON_CLASSES =
  "flex min-w-0 items-center gap-2.5 rounded-lg border bg-card p-2 text-left shadow-xs outline-none hover:bg-accent focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-60";

/** Содержимое карточки: монограмма [me], название организации и текущий филиал. */
function CardFace({ me }: { me: AuthMeResponse }) {
  return (
    <>
      <Monogram name={me.orgInfo.name} />
      <span className="flex min-w-0 flex-1 flex-col leading-tight">
        <span className="truncate font-semibold" title={me.orgInfo.name}>
          {me.orgInfo.name}
        </span>
        <span className="truncate text-xs text-muted-foreground" title={me.currentBranch.name}>
          {me.currentBranch.name}
        </span>
      </span>
    </>
  );
}

/** Декоративная монограмма организации [name] на основном цвете; размер — [className]. */
function Monogram({ name, className }: { name: string; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-lg bg-primary text-xs font-bold text-primary-foreground",
        className,
      )}
    >
      {monogramOf(name)}
    </span>
  );
}

/** Шапка списка: монограмма, название организации [me] и число доступных филиалов [count]. */
function ListHeader({ me, count }: { me: AuthMeResponse; count: number }) {
  const { t } = useI18n();
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <Monogram name={me.orgInfo.name} className="size-7 text-[11px]" />
      <span className="flex min-w-0 flex-col leading-tight">
        <span className="truncate font-semibold">{me.orgInfo.name}</span>
        <span className="truncate text-xs font-normal text-muted-foreground">
          {t("clubCard.branchCount", { count })}
        </span>
      </span>
    </span>
  );
}

/** Свойства раскрывающихся вариантов карточки. */
interface SwitcherProps {
  /** Клиент API для смены филиала. */
  readonly api: ApiClient;
  /** Сессия сотрудника. */
  readonly me: AuthMeResponse;
  /** Доступные сотруднику филиалы, не меньше двух. */
  readonly branches: readonly BranchDetailResponse[];
  /** Дополнительные классы карточки. */
  readonly className?: string | undefined;
}

/** Подпись кнопки-карточки для экранного диктора: организация, филиал и действие. */
function useCardLabel(me: AuthMeResponse): string {
  const { t } = useI18n();
  return t("clubCard.label", { org: me.orgInfo.name, branch: me.currentBranch.name });
}

/** Карточка с выпадающим списком филиалов — для боковой панели на компьютере. */
function BranchMenu({ api, me, branches, className }: SwitcherProps) {
  const { t } = useI18n();
  const switching = useSwitchBranch(api);
  const label = useCardLabel(me);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className={cn(BUTTON_CLASSES, className)}
        aria-label={label}
        disabled={switching.isPending}
      >
        <CardFace me={me} />
        <ChevronsUpDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-68 max-w-[calc(100vw-2rem)]">
        <DropdownMenuLabel>
          <ListHeader me={me} count={branches.length} />
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup
          value={me.currentBranch.id}
          onValueChange={(value) => {
            const branch = branches.find((b) => b.id === value);
            if (branch !== undefined && branch.id !== me.currentBranch.id) {
              switching.mutate(branch);
            }
          }}
        >
          {branches.map((branch) => (
            <DropdownMenuRadioItem key={branch.id} value={branch.id} className="min-h-9">
              <span className="truncate" title={branch.name}>
                {branch.name}
              </span>
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild className="min-h-9">
          <Link to="/settings/branches">
            <SettingsIcon aria-hidden />
            {t("clubCard.manageBranches")}
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Карточка со шторкой снизу — для верхней полосы и выезжающего меню на телефоне. */
function BranchSheet({ api, me, branches, className }: SwitcherProps) {
  const { t } = useI18n();
  const switching = useSwitchBranch(api);
  const label = useCardLabel(me);
  const [open, setOpen] = useState(false);
  const itemClasses =
    "flex min-h-11 w-full min-w-0 items-center gap-3 rounded-md px-3 text-left text-[15px] outline-none hover:bg-accent focus-visible:bg-accent focus-visible:ring-[3px] focus-visible:ring-ring/50";
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        className={cn(BUTTON_CLASSES, className)}
        aria-label={label}
        disabled={switching.isPending}
      >
        <CardFace me={me} />
        <ChevronsUpDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
      </SheetTrigger>
      <SheetContent
        side="bottom"
        aria-describedby={undefined}
        className="max-h-[85dvh] gap-1 overflow-y-auto rounded-t-2xl px-3 pt-4 pb-6"
      >
        <SheetTitle className="px-3 pb-2 text-base">
          <ListHeader me={me} count={branches.length} />
        </SheetTitle>
        <ul className="flex flex-col gap-0.5">
          {branches.map((branch) => {
            const current = branch.id === me.currentBranch.id;
            return (
              <li key={branch.id}>
                <button
                  type="button"
                  aria-current={current ? "true" : undefined}
                  className={cn(itemClasses, current && "bg-accent font-semibold")}
                  onClick={() => {
                    setOpen(false);
                    if (!current) {
                      switching.mutate(branch);
                    }
                  }}
                >
                  <span className="min-w-0 flex-1 truncate">{branch.name}</span>
                  {current && <CheckIcon aria-hidden className="size-4 shrink-0 text-primary" />}
                </button>
              </li>
            );
          })}
        </ul>
        <div className="my-1 h-px bg-border" />
        <Link
          to="/settings/branches"
          className={cn(itemClasses, "text-muted-foreground")}
          onClick={() => {
            setOpen(false);
          }}
        >
          <SettingsIcon aria-hidden className="size-4 shrink-0" />
          {t("clubCard.manageBranches")}
        </Link>
      </SheetContent>
    </Sheet>
  );
}
