<template>
  <main class="message-page">
    <button class="assistant-entry" @click="router.push('/ai-assistant')">
      <span class="assistant-entry__icon"><van-icon name="service-o" size="27" /></span>
      <span class="assistant-entry__content">
        <strong>27公寓找房助手</strong>
        <small>房源推荐、租住问答与看房预约</small>
      </span>
      <van-icon name="arrow" color="#969799" />
    </button>

    <section class="message-list">
      <h1>消息</h1>
      <van-empty image="message" description="暂无新消息" />
    </section>
  </main>
</template>

<script setup lang="ts" name="Message">
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<style scoped lang="less">
.message-page {
  min-height: calc(100vh - 50px);
  padding: 16px;
  background: var(--van-background);
}

.assistant-entry {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 15px;
  border: 1px solid var(--van-border-color);
  border-radius: 8px;
  background: var(--van-background-2);
  color: var(--van-text-color);
  text-align: left;
}

.assistant-entry__icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  place-items: center;
  border-radius: 8px;
  background: #2f7d5b;
  color: #fff;
}

.assistant-entry__content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;
}

.assistant-entry__content strong {
  font-size: 15px;
}

.assistant-entry__content small {
  overflow: hidden;
  color: var(--van-text-color-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-list h1 {
  margin: 24px 0 0;
  font-size: 16px;
}
</style>
