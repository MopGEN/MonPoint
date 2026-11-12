import { ref, watch, onMounted, onScopeDispose } from 'vue'
import { useRoute } from 'vue-router'

export function useDesplegable () {
  const zona = ref(null), boton = ref(null), abierto = ref(false)
  const ruta = useRoute()
  function cerrar () { abierto.value = false }
  function fuera (e) { if (!zona.value?.contains(e.target)) cerrar() }
  function teclado (e) {
    if (e.key === 'Escape' && abierto.value) { cerrar(); boton.value?.focus() }
  }
  onMounted(() => {
    document.addEventListener('pointerdown', fuera)
    document.addEventListener('focusin', fuera)
    document.addEventListener('keydown', teclado)
  })
  onScopeDispose(() => {
    document.removeEventListener('pointerdown', fuera)
    document.removeEventListener('focusin', fuera)
    document.removeEventListener('keydown', teclado)
  })
  watch(() => ruta.fullPath, cerrar)
  return { zona, boton, abierto, cerrar }
}
