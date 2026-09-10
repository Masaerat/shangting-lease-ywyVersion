import { expect, test } from "@playwright/test";

test("demo user completes fallback rental appointment loop", async ({ page }, testInfo) => {
  await page.goto("/#/login?redirect=%2Fai-assistant");
  await page.getByPlaceholder("请输入手机号").fill("13800000000");
  await page.getByPlaceholder("请输入验证码").fill("888888");
  await page.getByRole("button", { name: "登录" }).click();

  await expect(page).toHaveURL(/#\/ai-assistant/);
  await page.getByTestId("chat-input").fill("预算2500元，并说明押金怎么退");
  await page.getByTestId("send-message").click();
  await expect(page.getByText("降级模式")).toBeVisible();
  await expect(page.getByText("参考依据")).toBeVisible();
  await expect(page.getByText("预约看房").first()).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("assistant.png"), fullPage: true });

  await page.getByText("预约看房").first().click();
  await page.getByTestId("name-input").fill("演示用户");
  await page.getByTestId("phone-input").fill("13800000000");
  await page.getByTestId("create-draft").click();
  await expect(page.getByTestId("confirm-appointment")).toBeVisible();
  await page.getByTestId("confirm-appointment").click();

  await expect(page).toHaveURL(/#\/myAppointment\?appointmentId=\d+/);
  await expect(page.getByText("预约时间").first()).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("appointment.png"), fullPage: true });
});
