<template>
  <div>
    <svg-icon icon-class="question" @click="goto" />
  </div>
</template>

<script setup lang="ts">
const url = ref<string>('http://doc.wendao.vip/wendao-vue')

function goto(): void {
  window.open(url.value)
}
</script>