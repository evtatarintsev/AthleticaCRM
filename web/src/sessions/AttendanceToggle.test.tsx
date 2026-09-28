import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import { AttendanceToggle } from "./AttendanceToggle";
import type { AttendanceStatus } from "./sessionDetailMock";

describe("AttendanceToggle", () => {
  it.each<[string, AttendanceStatus]>([
    [ru["sessionDetail.attendance.PRESENT"], "PRESENT"],
    [ru["sessionDetail.attendance.ABSENT"], "ABSENT"],
    [ru["sessionDetail.attendance.SICK"], "SICK"],
    [ru["sessionDetail.attendance.LATE"], "LATE"],
  ])("клик по «%s» вызывает callback со статусом %s", async (label, status) => {
    const onChange = vi.fn();
    renderPage(<AttendanceToggle value="ABSENT" onChange={onChange} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: label }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(status);
  });

  it("отмечает текущий статус как активный", async () => {
    renderPage(<AttendanceToggle value="SICK" onChange={vi.fn()} />);

    expect(
      await screen.findByRole("button", { name: ru["sessionDetail.attendance.SICK"] }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByRole("button", { name: ru["sessionDetail.attendance.PRESENT"] }),
    ).toHaveAttribute("aria-pressed", "false");
  });
});
