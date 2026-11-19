import { useEffect, useState } from "react";

/** Текущий момент, обновляемый на границе минуты: часы главной и расписания. */
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
