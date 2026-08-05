import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it, vi } from "vitest";
import AiAssistant from "./aiAssistant.vue";

const { streamAiChatMock } = vi.hoisted(() => ({
  streamAiChatMock: vi.fn()
}));

vi.mock("@/api/ai", () => ({
  streamAiChat: streamAiChatMock
}));

describe("AI rental assistant", () => {
  it("renders streaming recommendations and citations", async () => {
    streamAiChatMock.mockImplementation(async (_request, onEvent) => {
      onEvent({
        type: "meta",
        payload: { mode: "FALLBACK", conversationId: "conversation-1" }
      });
      onEvent({ type: "message", payload: "为你找到一套合适房源。" });
      onEvent({
        type: "recommendations",
        payload: [
          {
            roomId: 930001,
            apartmentId: 920001,
            apartment: "安心公寓",
            roomNumber: "A101",
            rent: 2400
          }
        ]
      });
      onEvent({
        type: "citations",
        payload: [
          {
            roomId: null,
            apartment: "押金与付款说明",
            roomNumber: "",
            rent: null,
            source: "本地租房知识"
          }
        ]
      });
      onEvent({ type: "done", payload: "conversation-1" });
    });

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", component: { template: "<div />" } }]
    });
    const wrapper = mount(AiAssistant, {
      global: { plugins: [createPinia(), router] }
    });

    await wrapper.get('[data-test="chat-input"]').setValue("预算 2500 元，押金怎么退？");
    await wrapper.get('[data-test="send-message"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("降级模式");
    expect(wrapper.text()).toContain("安心公寓");
    expect(wrapper.text()).toContain("本地租房知识");
  });
});
