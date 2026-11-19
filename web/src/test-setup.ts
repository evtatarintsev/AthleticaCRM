import * as matchers from "@testing-library/jest-dom/matchers";
import { cleanup } from "@testing-library/react";
import { afterEach, expect, vi } from "vitest";
import { stubMatchMedia } from "./test/media";

expect.extend(matchers);

Element.prototype.scrollIntoView = vi.fn<Element["scrollIntoView"]>();

stubMatchMedia(true);

afterEach(() => {
  cleanup();
  stubMatchMedia(true);
});
