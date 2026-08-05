<template>
  <article class="room-card" @click="openRoom">
    <div class="room-card__main">
      <div>
        <h3>{{ room.apartment }} · {{ room.roomNumber }}</h3>
        <p>可预约房源</p>
      </div>
      <div class="room-card__price">
        <strong>¥{{ room.rent }}</strong>
        <span>/月</span>
      </div>
    </div>
    <div class="room-card__actions">
      <span>查看详情</span>
      <van-button
        size="small"
        type="primary"
        icon="calendar-o"
        @click.stop="$emit('appoint', room)"
      >
        预约看房
      </van-button>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { AiRecommendation } from "@/api/ai/types";
import { useRouter } from "vue-router";

const props = defineProps<{ room: AiRecommendation }>();
defineEmits<{ (event: "appoint", room: AiRecommendation): void }>();

const router = useRouter();
const openRoom = () => {
  router.push({ path: "/roomDetail", query: { id: props.room.roomId } });
};
</script>

<style scoped lang="less">
.room-card {
  padding: 14px;
  border: 1px solid var(--van-border-color);
  border-radius: 8px;
  background: var(--van-background-2);
  cursor: pointer;
}

.room-card__main,
.room-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h3,
p {
  margin: 0;
}

h3 {
  font-size: 15px;
}

p,
.room-card__actions,
.room-card__price span {
  margin-top: 6px;
  color: var(--van-text-color-2);
  font-size: 12px;
}

.room-card__price {
  flex: 0 0 auto;
  color: var(--van-danger-color);
}

.room-card__price strong {
  font-size: 19px;
}

.room-card__actions {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--van-border-color);
}
</style>
