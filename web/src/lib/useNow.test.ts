import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { useNow } from "./useNow";

afterEach(() => {
  vi.useRealTimers();
});

it("тикает на границе минуты и в полночь, очищает таймер", () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(2026, 10, 18, 23, 58, 42, 500));
  const { result, unmount } = renderHook(useNow);
  act(() => {
    vi.advanceTimersByTime(17_499);
  });
  expect(result.current).toEqual(new Date(2026, 10, 18, 23, 58, 42, 500));
  act(() => {
    vi.advanceTimersByTime(1);
  });
  expect(result.current).toEqual(new Date(2026, 10, 18, 23, 59));
  act(() => {
    vi.advanceTimersByTime(60_000);
  });
  expect(result.current).toEqual(new Date(2026, 10, 19));
  unmount();
  expect(vi.getTimerCount()).toBe(0);
});
