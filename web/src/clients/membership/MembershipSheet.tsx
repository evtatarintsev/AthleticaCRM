import type { ReactNode } from "react";
import type { LocalDate, MembershipSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import {
  EndingMark,
  MembershipPill,
  MembershipSessions,
  MembershipSessionsBar,
} from "./MembershipParts";
import { isCurrentMembership, membershipView } from "./membershipView";

/**
 * Панель абонемента [membership] справа поверх карточки клиента: состояние на дату [today],
 * период, занятия, стоимость и действия. Панель открыта при [open] и найденном абонементе;
 * закрытие сообщается через [onOpenChange].
 */
export function MembershipSheet({
  membership,
  open,
  today,
  onOpenChange,
}: {
  /** Абонемент панели; `null` — абонемент не выбран или его нет в загруженных данных. */
  readonly membership: MembershipSchema | null;
  readonly open: boolean;
  readonly today: LocalDate;
  readonly onOpenChange: (open: boolean) => void;
}) {
  return (
    <EditSheet
      open={open && membership !== null}
      onOpenChange={onOpenChange}
      title={membership?.name ?? ""}
      size="md"
    >
      {membership !== null && <MembershipPanel membership={membership} today={today} />}
    </EditSheet>
  );
}

/**
 * Содержимое панели абонемента [membership] на дату [today]. Действия пока ничего не делают:
 * продление, заморозка и закрытие появятся вместе с моделью состояний на сервере
 * (https://github.com/evtatarintsev/AthleticaCRM/issues/162).
 */
function MembershipPanel({
  membership,
  today,
}: {
  readonly membership: MembershipSchema;
  readonly today: LocalDate;
}) {
  const { t, format } = useI18n();
  const view = membershipView(membership, today);
  const current = isCurrentMembership(view);
  return (
    <EditSheetBody>
      <div>
        <MembershipPill phase={view.phase} />
      </div>
      <dl className="divide-y rounded-lg border bg-card text-sm">
        <Field label={t("clientCard.membership.period")}>
          {format.date(membership.startDate)} —{" "}
          <EndingMark active={view.endingByDate}>{format.date(membership.endDate)}</EndingMark>
        </Field>
        <Field label={t("clientCard.membership.sessions")}>
          <EndingMark active={view.endingBySessions}>
            <MembershipSessions membership={membership} />
          </EndingMark>
          <MembershipSessionsBar membership={membership} />
        </Field>
        <Field label={t("clientCard.membership.price")}>
          <span className="tabular-nums">{format.money(membership.price)}</span>
        </Field>
      </dl>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline">
          {t("clientCard.membership.renew")}
        </Button>
        {current && (
          <>
            <Button type="button" variant="outline">
              {t("clientCard.membership.freeze")}
            </Button>
            <Button
              type="button"
              variant="outline"
              aria-label={t("clientCard.membership.closeLabel")}
            >
              {t("clientCard.membership.close")}
            </Button>
          </>
        )}
      </div>
    </EditSheetBody>
  );
}

/** Строка сведений панели: подпись [label] и значение. */
function Field({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8rem_1fr] gap-3 px-4 py-2.5">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  );
}
