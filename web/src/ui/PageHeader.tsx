import type { ReactNode } from "react";

/**
 * Заголовок страницы [title], необязательная строка под ним [description]
 * и необязательные действия [actions] справа.
 */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  const heading = <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>;
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      {description === undefined ? (
        heading
      ) : (
        <div className="min-w-0">
          {heading}
          <p className="mt-0.5 text-sm text-muted-foreground">{description}</p>
        </div>
      )}
      {actions}
    </div>
  );
}
