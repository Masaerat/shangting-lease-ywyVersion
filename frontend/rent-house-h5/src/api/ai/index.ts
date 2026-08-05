import http from "@/utils/http";
import { getToken } from "@/utils/token";
import type {
  AiChatRequest,
  AiSseEvent,
  AppointmentConfirmResponse,
  AppointmentDraftRequest,
  AppointmentDraftResponse
} from "./types";

const apiBaseUrl = import.meta.env.PROD
  ? import.meta.env.VITE_APP_BASE_URL || ""
  : "";

function dispatchFrame(frame: string, onEvent: (event: AiSseEvent) => void) {
  const data = frame
    .split(/\r?\n/)
    .filter(line => line.startsWith("data:"))
    .map(line => line.slice(5).trimStart())
    .join("\n");

  if (data) {
    onEvent(JSON.parse(data) as AiSseEvent);
  }
}

export async function streamAiChat(
  request: AiChatRequest,
  onEvent: (event: AiSseEvent) => void,
  signal?: AbortSignal
) {
  const token = getToken();
  const response = await fetch(`${apiBaseUrl}/app/ai/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { "access-token": token } : {})
    },
    body: JSON.stringify(request),
    signal
  });

  if (!response.ok) {
    throw new Error(`AI 服务请求失败 (${response.status})`);
  }
  if (!response.body) {
    throw new Error("当前浏览器不支持流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, "\n");
    const frames = buffer.split("\n\n");
    buffer = frames.pop() || "";
    frames.forEach(frame => dispatchFrame(frame, onEvent));
    if (done) break;
  }

  if (buffer.trim()) {
    dispatchFrame(buffer, onEvent);
  }
}

export function createAppointmentDraft(request: AppointmentDraftRequest) {
  return http.post<AppointmentDraftResponse>(
    "/app/ai/appointments/draft",
    request
  );
}

export function confirmAppointment(confirmationToken: string) {
  return http.post<AppointmentConfirmResponse>(
    "/app/ai/appointments/confirm",
    { confirmationToken }
  );
}
