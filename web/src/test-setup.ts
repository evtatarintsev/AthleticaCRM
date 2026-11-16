import * as matchers from "@testing-library/jest-dom/matchers";
import { cleanup } from "@testing-library/react";
import { afterEach, expect, vi } from "vitest";

expect.extend(matchers);

Element.prototype.scrollIntoView = vi.fn<Element["scrollIntoView"]>();

afterEach(() => {
  cleanup();
});
