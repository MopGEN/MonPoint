import { defineStore } from 'pinia'
import { estadoInicial } from '../caja/cobro.js'

export const useCaja = defineStore('caja', {
  state: () => ({ cobro: estadoInicial() }),
  actions: {
    limpiarSesion() {
      const pendientes = this.cobro.pendientes
      // Mantener identidad: la máquina puede estar archivando un cobro en vuelo.
      Object.assign(this.cobro, estadoInicial(), { pendientes })
    },
  },
})
