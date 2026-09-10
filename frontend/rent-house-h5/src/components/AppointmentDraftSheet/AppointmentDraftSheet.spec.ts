import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import AppointmentDraftSheet from "./AppointmentDraftSheet.vue";

const { createAppointmentDraftMock, confirmAppointmentMock } = vi.hoisted(
  () => ({
    createAppointmentDraftMock: vi.fn(),
    confirmAppointmentMock: vi.fn()
  })
);

vi.mock("@/api/ai", () => ({
  createAppointmentDraft: createAppointmentDraftMock,
  confirmAppointment: confirmAppointmentMock
}));

describe("appointment confirmation sheet", () => {
  it("does not confirm before a separate explicit click", async () => {
    createAppointmentDraftMock.mockResolvedValue({
      data: {
        confirmationToken: "draft-token",
        expiresAt: "2026-08-05T04:00:00Z",
        roomId: 930001,
        apartmentId: 920001,
        name: "演示用户",
        phone: "13800000000",
        appointmentTime: "2026-08-06T14:00:00",
        additionalInfo: ""
      }
    });
    confirmAppointmentMock.mockResolvedValue({
      data: { appointmentId: 880001, idempotentReplay: false }
    });

    const wrapper = mount(AppointmentDraftSheet, {
      props: {
        room: {
          roomId: 930001,
          apartmentId: 920001,
          apartment: "安心公寓",
          roomNumber: "A101",
          rent: 2400
        },
        open: true
      }
    });

    await wrapper.get('[data-test="name-input"]').setValue("演示用户");
    await wrapper.get('[data-test="phone-input"]').setValue("13800000000");
    await wrapper
      .get('[data-test="appointment-time-input"]')
      .setValue("2026-08-06T14:00");
    await wrapper.get('[data-test="create-draft"]').trigger("click");
    await flushPromises();

    expect(createAppointmentDraftMock).toHaveBeenCalledTimes(1);
    expect(confirmAppointmentMock).not.toHaveBeenCalled();

    await wrapper.get('[data-test="confirm-appointment"]').trigger("click");
    await flushPromises();

    expect(confirmAppointmentMock).toHaveBeenCalledTimes(1);
  });
});
