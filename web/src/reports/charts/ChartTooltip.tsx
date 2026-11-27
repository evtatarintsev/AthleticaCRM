/** Подсказка графика: одна готовая строка [text]; без неё ничего не рисуется. */
export function ChartTooltip({ text }: { readonly text: string | undefined }) {
  if (text === undefined) {
    return null;
  }
  return (
    <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-sm shadow-sm">
      {text}
    </div>
  );
}
