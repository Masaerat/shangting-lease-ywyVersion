<template>
  <main class="assistant-page">
    <header class="assistant-header">
      <div class="assistant-header__icon"><van-icon name="service-o" size="25" /></div>
      <div>
        <h1>27公寓找房助手</h1>
        <p>在线 · 房源、租住规则与看房预约</p>
      </div>
    </header>

    <section ref="conversationRef" class="conversation" aria-live="polite">
      <div class="welcome">
        <strong>你好，我是你的找房助手</strong>
        <p>告诉我预算、区域和入住偏好，我会结合实时房源回答。</p>
        <div class="welcome__suggestions">
          <button v-for="suggestion in suggestions" :key="suggestion" @click="sendSuggestion(suggestion)">
            {{ suggestion }}
          </button>
        </div>
      </div>

      <article v-for="turn in turns" :key="turn.id" class="turn">
        <div class="bubble bubble--user">{{ turn.question }}</div>
        <div class="answer">
          <div class="answer__status">
            <span v-if="turn.mode" :class="['mode', `mode--${turn.mode.toLowerCase()}`]">
              {{ turn.mode === "FALLBACK" ? "降级模式" : "模型模式" }}
            </span>
            <span v-if="turn.status === 'streaming'">正在整理...</span>
          </div>
          <p v-if="turn.answer" class="answer__text">{{ turn.answer }}</p>
          <p v-if="turn.error" class="answer__error">{{ turn.error }}</p>
          <div v-if="turn.recommendations.length" class="room-list">
            <AiRoomCard
              v-for="room in turn.recommendations"
              :key="room.roomId"
              :room="room"
              @appoint="openAppointment"
            />
          </div>
          <AiCitationList :citations="turn.citations" />
        </div>
      </article>
    </section>

    <footer class="composer">
      <textarea
        v-model.trim="input"
        data-test="chat-input"
        rows="1"
        maxlength="300"
        :disabled="sending"
        placeholder="输入预算、区域或租住问题"
        @keydown.enter.exact.prevent="sendMessage"
      />
      <van-button
        data-test="send-message"
        type="primary"
        icon="guide-o"
        :loading="sending"
        :disabled="!input || sending"
        aria-label="发送"
        @click="sendMessage"
      />
    </footer>

    <AppointmentDraftSheet
      v-if="selectedRoom"
      v-model:open="appointmentOpen"
      :room="selectedRoom"
      @confirmed="appointmentConfirmed"
    />
  </main>
</template>

<script setup lang="ts" name="AiAssistant">
import { streamAiChat } from "@/api/ai";
import type {
  AiCitation,
  AiMode,
  AiRecommendation,
  AiSseEvent
} from "@/api/ai/types";
import AiCitationList from "@/components/AiCitationList/AiCitationList.vue";
import AiRoomCard from "@/components/AiRoomCard/AiRoomCard.vue";
import AppointmentDraftSheet from "@/components/AppointmentDraftSheet/AppointmentDraftSheet.vue";
import { nextTick, ref } from "vue";
import { useRouter } from "vue-router";

interface ChatTurn {
  id: number;
  question: string;
  answer: string;
  mode: AiMode | null;
  status: "streaming" | "done" | "error";
  recommendations: AiRecommendation[];
  citations: AiCitation[];
  error: string;
}

const router = useRouter();
const input = ref("");
const sending = ref(false);
const turns = ref<ChatTurn[]>([]);
const conversationId = ref("");
const conversationRef = ref<HTMLElement>();
const selectedRoom = ref<AiRecommendation | null>(null);
const appointmentOpen = ref(false);
const suggestions = ["预算2500元以内", "押金怎么退", "推荐交通方便的房源"];

function handleEvent(turn: ChatTurn, event: AiSseEvent) {
  switch (event.type) {
    case "meta":
      turn.mode = event.payload.mode;
      conversationId.value = event.payload.conversationId;
      break;
    case "message":
      turn.answer += event.payload;
      break;
    case "recommendations":
      turn.recommendations = event.payload;
      break;
    case "citations":
      turn.citations = event.payload;
      break;
    case "done":
      turn.status = "done";
      break;
    case "error":
      turn.error = event.payload;
      turn.status = "error";
      break;
  }
  scrollToLatest();
}

async function sendMessage() {
  const question = input.value.trim();
  if (!question || sending.value) return;

  const turn: ChatTurn = {
    id: Date.now(),
    question,
    answer: "",
    mode: null,
    status: "streaming",
    recommendations: [],
    citations: [],
    error: ""
  };
  turns.value.push(turn);
  input.value = "";
  sending.value = true;
  scrollToLatest();

  try {
    await streamAiChat(
      { message: question, conversationId: conversationId.value || undefined },
      event => handleEvent(turn, event)
    );
    if (turn.status === "streaming") turn.status = "done";
  } catch (cause) {
    turn.status = "error";
    turn.error = cause instanceof Error ? cause.message : "连接中断，请稍后重试";
  } finally {
    sending.value = false;
    scrollToLatest();
  }
}

function sendSuggestion(suggestion: string) {
  if (sending.value) return;
  input.value = suggestion;
  sendMessage();
}

function openAppointment(room: AiRecommendation) {
  selectedRoom.value = room;
  appointmentOpen.value = true;
}

function appointmentConfirmed(appointmentId: number) {
  router.push({ path: "/myAppointment", query: { appointmentId } });
}

async function scrollToLatest() {
  await nextTick();
  conversationRef.value?.scrollTo({
    top: conversationRef.value.scrollHeight,
    behavior: "smooth"
  });
}
</script>

<style scoped lang="less">
.assistant-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: calc(100vh - 50px);
  min-height: 560px;
  background: var(--van-background);
}

.assistant-header {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--van-border-color);
  background: var(--van-background-2);
}

.assistant-header__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: #2f7d5b;
  color: #fff;
}

h1,
.assistant-header p {
  margin: 0;
}

h1 {
  font-size: 16px;
}

.assistant-header p {
  margin-top: 4px;
  color: var(--van-text-color-2);
  font-size: 11px;
}

.conversation {
  overflow-y: auto;
  padding: 16px 14px 24px;
}

.welcome,
.answer {
  max-width: 620px;
  padding: 14px;
  border: 1px solid var(--van-border-color);
  border-radius: 8px;
  background: var(--van-background-2);
}

.welcome {
  margin: 0 auto 22px;
}

.welcome p {
  margin: 7px 0 12px;
  color: var(--van-text-color-2);
  font-size: 13px;
  line-height: 1.6;
}

.welcome__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.welcome__suggestions button {
  padding: 7px 9px;
  border: 1px solid #8ab59f;
  border-radius: 6px;
  background: transparent;
  color: #287052;
  font-size: 12px;
}

.turn {
  max-width: 680px;
  margin: 0 auto 20px;
}

.bubble {
  width: fit-content;
  max-width: 82%;
  margin: 0 0 10px auto;
  padding: 10px 12px;
  border-radius: 8px 2px 8px 8px;
  background: #2f7d5b;
  color: #fff;
  font-size: 14px;
  line-height: 1.55;
}

.answer__status {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  color: var(--van-text-color-3);
  font-size: 11px;
}

.mode {
  padding: 3px 6px;
  border-radius: 4px;
  background: #e8f4ee;
  color: #246448;
}

.mode--fallback {
  background: #fff3d6;
  color: #835b00;
}

.answer__text {
  margin: 9px 0 0;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.7;
}

.answer__error {
  margin: 9px 0 0;
  color: var(--van-danger-color);
  font-size: 12px;
}

.room-list {
  display: grid;
  gap: 10px;
  margin: 14px 0;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  gap: 8px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--van-border-color);
  background: var(--van-background-2);
}

.composer textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  max-height: 100px;
  padding: 11px 12px;
  resize: none;
  border: 1px solid var(--van-border-color);
  border-radius: 7px;
  outline: none;
  background: var(--van-background);
  color: var(--van-text-color);
  font: inherit;
  line-height: 20px;
}

.composer textarea:focus {
  border-color: var(--van-primary-color);
}

@media (min-width: 768px) {
  .assistant-page {
    height: 100vh;
  }
}
</style>
