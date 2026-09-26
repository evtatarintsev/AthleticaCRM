import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientId, MembershipSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { cn } from "@/lib/utils";

/** Абонементы клиента: история выданных абонементов и переход к выдаче нового. */
export function ClientSubscriptionsSection({
  api,
  branchId,
  clientId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const membershipsQuery = apiQuery(api, branchId, "memberships/list", { clientId });
  const memberships = useQuery(membershipsQuery);

  return (
    <div className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold">{t("clients.detail.subscriptions")}</h2>
        <Button asChild size="sm">
          <Link to="/clients/$clientId/issue-subscription" params={{ clientId }}>
            {t("clients.detail.issueSubscription")}
          </Link>
        </Button>
      </div>

      {memberships.isError && <FormAlert message={t("clients.detail.loadError")} />}
      {memberships.data !== undefined &&
        (memberships.data.memberships.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("clients.detail.subscriptionsEmpty")}</p>
        ) : (
          <ul className="divide-y">
            {memberships.data.memberships.map((membership) => (
              <MembershipRow key={membership.id} membership={membership} />
            ))}
          </ul>
        ))}
    </div>
  );
}

function MembershipRow({ membership }: { readonly membership: MembershipSchema }) {
  const { t, format } = useI18n();
  const sessions =
    membership.sessionsTotal === null
      ? t("clients.detail.subscriptionUnlimited")
      : t("clients.detail.subscriptionSessions", {
          remaining: membership.sessionsRemaining ?? 0,
          total: membership.sessionsTotal,
        });
  return (
    <li className="space-y-1 py-3 first:pt-0">
      <div className="flex items-baseline justify-between gap-2">
        <span className="font-medium">{membership.name}</span>
        <span className="font-medium">{format.money(membership.price)}</span>
      </div>
      <p className="text-sm text-muted-foreground">{sessions}</p>
      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <span>
          {format.date(membership.startDate)} — {format.date(membership.endDate)}
        </span>
        <span
          className={cn(
            "rounded-full px-2 py-0.5",
            membership.status === "ACTIVE"
              ? "bg-primary/10 text-primary"
              : "bg-secondary text-secondary-foreground",
          )}
        >
          {t(
            membership.status === "ACTIVE"
              ? "clients.detail.subscriptionActive"
              : "clients.detail.subscriptionExpired",
          )}
        </span>
      </p>
    </li>
  );
}
