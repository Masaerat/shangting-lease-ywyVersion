import { config } from "@vue/test-utils";

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver = ResizeObserverStub;
Element.prototype.scrollTo = () => undefined;
config.global.stubs = {
  teleport: true
};
