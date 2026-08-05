export type AiMode = "MODEL" | "FALLBACK";

export interface AiChatRequest {
  message: string;
  conversationId?: string;
}

export interface AiChatMeta {
  mode: AiMode;
  conversationId: string;
}

export interface AiRecommendation {
  roomId: number;
  apartmentId: number;
  apartment: string;
  roomNumber: string;
  rent: number;
}

export interface AiCitation {
  roomId: number | null;
  apartment: string;
  roomNumber: string;
  rent: number | null;
  source: string;
}

export type AiSseEvent =
  | { type: "meta"; payload: AiChatMeta }
  | { type: "message"; payload: string }
  | { type: "recommendations"; payload: AiRecommendation[] }
  | { type: "citations"; payload: AiCitation[] }
  | { type: "done"; payload: string | null }
  | { type: "error"; payload: string };

export interface AppointmentDraftRequest {
  roomId: number;
  name: string;
  phone: string;
  appointmentTime: string;
  additionalInfo?: string;
}

export interface AppointmentDraftResponse extends AppointmentDraftRequest {
  confirmationToken: string;
  expiresAt: string;
  apartmentId: number;
}

export interface AppointmentConfirmResponse {
  appointmentId: number;
  idempotentReplay: boolean;
}
