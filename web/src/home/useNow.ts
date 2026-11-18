import { useEffect, useState } from "react";

/** Общие часы главной, обновляемые на границе минуты. */
export function useNow(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const schedule = () => {
      timer = setTimeout(
        () => {
          setNow(new Date());
          schedule();
        },
        60_000 - (Date.now() % 60_000),
      );
    };
    schedule();
    return () => {
      clearTimeout(timer);
    };
  }, []);
  return now;
}
