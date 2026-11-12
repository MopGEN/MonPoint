import { computed, ref, watch, onScopeDispose } from 'vue'
import { defineStore } from 'pinia'

// Solo persiste una preferencia visual; la sesion y su token siguen en memoria.
const CLAVE = 'monpoint.tema'
export const usePreferencias = defineStore('preferencias', () => {
  const consulta = window.matchMedia('(prefers-color-scheme: dark)')
  const sistemaOscuro = ref(consulta.matches)
  let guardado = null
  try { guardado = localStorage.getItem(CLAVE) } catch { /* almacenamiento bloqueado */ }
  const elegido = ref(['claro', 'oscuro'].includes(guardado) ? guardado : null)
  const tema = computed(() => elegido.value ?? (sistemaOscuro.value ? 'oscuro' : 'claro'))
  const colapsada = ref(false)
  function cambioSistema (e) { sistemaOscuro.value = e.matches }
  consulta.addEventListener('change', cambioSistema)
  onScopeDispose(() => consulta.removeEventListener('change', cambioSistema))
  watch(tema, (valor) => { document.documentElement.dataset.tema = valor }, { immediate: true })
  function alternarTema () {
    elegido.value = tema.value === 'claro' ? 'oscuro' : 'claro'
    try { localStorage.setItem(CLAVE, elegido.value) } catch { /* funciona en esta pestaña */ }
  }
  return { tema, colapsada, alternarTema }
})
