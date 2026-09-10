<template>
  <van-action-sheet v-model:show="visible" title="确认看房预约">
    <div class="draft-sheet">
      <div class="draft-sheet__room">
        <div>
          <strong>{{ room.apartment }} · {{ room.roomNumber }}</strong>
          <span>¥{{ room.rent }}/月</span>
        </div>
        <van-tag type="primary" plain>待确认</van-tag>
      </div>

      <template v-if="!draft">
        <label>
          姓名
          <input
            v-model.trim="form.name"
            data-test="name-input"
            autocomplete="name"
            placeholder="请输入看房人姓名"
          />
        </label>
        <label>
          手机号
          <input
            v-model.trim="form.phone"
            data-test="phone-input"
            type="tel"
            autocomplete="tel"
            maxlength="11"
            placeholder="请输入手机号"
          />
        </label>
        <label>
          看房时间
          <input
            v-model="form.appointmentTime"
            data-test="appointment-time-input"
            type="datetime-local"
          />
        </label>
        <label>
          备注
          <textarea
            v-model.trim="form.additionalInfo"
            rows="2"
            maxlength="100"
            placeholder="选填，例如希望提前电话联系"
          />
        </label>
        <p v-if="error" class="draft-sheet__error">{{ error }}</p>
        <van-button
          block
          type="primary"
          :loading="creating"
          :disabled="!canCreate"
          data-test="create-draft"
          @click="createDraft"
        >
          生成预约草稿
        </van-button>
      </template>

      <template v-else>
        <div class="draft-sheet__confirmation">
          <van-icon name="shield-o" size="28" />
          <strong>请再次核对预约信息</strong>
          <p>{{ draft.name }} · {{ draft.phone }}</p>
          <p>{{ formatTime(draft.appointmentTime) }}</p>
          <small>草稿将在 10 分钟后失效，点击确认后才会创建预约。</small>
        </div>
        <p v-if="error" class="draft-sheet__error">{{ error }}</p>
        <div class="draft-sheet__buttons">
          <van-button block :disabled="confirming" @click="draft = null">
            返回修改
          </van-button>
          <van-button
            block
            type="primary"
            :loading="confirming"
            data-test="confirm-appointment"
            @click="confirmDraft"
          >
            明确确认预约
          </van-button>
        </div>
      </template>
    </div>
  </van-action-sheet>
</template>

<script setup lang="ts">
import {
  confirmAppointment,
  createAppointmentDraft
} from "@/api/ai";
import type {
  AiRecommendation,
  AppointmentDraftResponse
} from "@/api/ai/types";
import dayjs from "dayjs";
import { computed, reactive, ref, watch } from "vue";

const props = defineProps<{
  room: AiRecommendation;
  open: boolean;
}>();
const emit = defineEmits<{
  (event: "update:open", value: boolean): void;
  (event: "confirmed", appointmentId: number): void;
}>();

const visible = computed({
  get: () => props.open,
  set: value => emit("update:open", value)
});
const form = reactive({
  name: "",
  phone: "",
  appointmentTime: dayjs().add(1, "day").hour(14).minute(0).format("YYYY-MM-DDTHH:mm"),
  additionalInfo: ""
});
const draft = ref<AppointmentDraftResponse | null>(null);
const creating = ref(false);
const confirming = ref(false);
const error = ref("");
const canCreate = computed(
  () =>
    form.name.length > 0 &&
    /^1\d{10}$/.test(form.phone) &&
    Boolean(form.appointmentTime)
);

watch(
  () => props.room.roomId,
  () => {
    draft.value = null;
    error.value = "";
  }
);

async function createDraft() {
  if (!canCreate.value || creating.value) return;
  creating.value = true;
  error.value = "";
  try {
    const response = await createAppointmentDraft({
      roomId: props.room.roomId,
      ...form
    });
    draft.value = response.data;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "预约草稿创建失败";
  } finally {
    creating.value = false;
  }
}

async function confirmDraft() {
  if (!draft.value || confirming.value) return;
  confirming.value = true;
  error.value = "";
  try {
    const response = await confirmAppointment(draft.value.confirmationToken);
    emit("confirmed", response.data.appointmentId);
    visible.value = false;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "预约确认失败";
  } finally {
    confirming.value = false;
  }
}

const formatTime = (value: string) => dayjs(value).format("YYYY年M月D日 HH:mm");
</script>

<style scoped lang="less">
.draft-sheet {
  padding: 0 16px calc(20px + env(safe-area-inset-bottom));
}

.draft-sheet__room,
.draft-sheet__buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.draft-sheet__room {
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  background: var(--van-background);
}

.draft-sheet__room strong,
.draft-sheet__room span {
  display: block;
}

.draft-sheet__room span {
  margin-top: 5px;
  color: var(--van-danger-color);
  font-size: 13px;
}

label {
  display: block;
  margin-bottom: 13px;
  color: var(--van-text-color-2);
  font-size: 13px;
}

input,
textarea {
  box-sizing: border-box;
  display: block;
  width: 100%;
  margin-top: 6px;
  padding: 11px 12px;
  border: 1px solid var(--van-border-color);
  border-radius: 6px;
  outline: none;
  background: var(--van-background-2);
  color: var(--van-text-color);
  font: inherit;
}

input:focus,
textarea:focus {
  border-color: var(--van-primary-color);
}

.draft-sheet__confirmation {
  padding: 18px 12px;
  text-align: center;
}

.draft-sheet__confirmation strong,
.draft-sheet__confirmation p,
.draft-sheet__confirmation small {
  display: block;
  margin: 8px 0 0;
}

.draft-sheet__confirmation small {
  color: var(--van-text-color-2);
  line-height: 1.6;
}

.draft-sheet__error {
  color: var(--van-danger-color);
  font-size: 12px;
}

.draft-sheet__buttons > * {
  flex: 1;
}
</style>
