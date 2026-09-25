import type { LinkProps, RegisteredRouter } from "@tanstack/react-router";
import { describe, expectTypeOf, it } from "vitest";
import type { ClientId } from "@/api/generated/contracts";
import type { StaticAppPath } from "./router";

/**
 * Проверки типов ссылок роутера. Тип роутера содержит `any` из объявлений TanStack Router,
 * поэтому файл исключён из `type-coverage` (см. `package.json`, `web/README.md`).
 */
describe("типы маршрутов", () => {
  it("ссылка на несуществующий маршрут не компилируется", () => {
    expectTypeOf<"/login">().toExtend<StaticAppPath>();
    expectTypeOf<"/nope">().not.toExtend<StaticAppPath>();
    expectTypeOf<{ to: "/nope" }>().not.toExtend<
      LinkProps<"a", RegisteredRouter, string, "/nope">
    >();
  });

  it("ссылка без обязательного параметра или с параметром не того типа не компилируется", () => {
    type ClientLink = LinkProps<"a", RegisteredRouter, string, "/clients/$clientId">;
    expectTypeOf<{
      to: "/clients/$clientId";
      params: { clientId: ClientId };
    }>().toExtend<ClientLink>();
    expectTypeOf<{ to: "/clients/$clientId" }>().not.toExtend<ClientLink>();
    expectTypeOf<{
      to: "/clients/$clientId";
      params: { clientId: string };
    }>().not.toExtend<ClientLink>();
  });
});
