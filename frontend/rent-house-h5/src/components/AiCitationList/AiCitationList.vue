<template>
  <section v-if="citations.length" class="citations">
    <h3><van-icon name="records-o" /> 参考依据</h3>
    <ul>
      <li v-for="(citation, index) in citations" :key="citationKey(citation, index)">
        <span>{{ citation.source }}</span>
        <small v-if="citation.apartment">{{ citation.apartment }}</small>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import type { AiCitation } from "@/api/ai/types";

defineProps<{ citations: AiCitation[] }>();

const citationKey = (citation: AiCitation, index: number) =>
  `${citation.source}-${citation.roomId ?? "doc"}-${index}`;
</script>

<style scoped lang="less">
.citations {
  padding-top: 12px;
  border-top: 1px solid var(--van-border-color);
}

h3 {
  margin: 0 0 8px;
  color: var(--van-text-color-2);
  font-size: 13px;
  font-weight: 500;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  font-size: 12px;
}

small {
  overflow: hidden;
  color: var(--van-text-color-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
