import { defineStore } from 'pinia'
import { estadoInicial } from '../caja/cobro.js'

export const useCaja = defineStore('caja', {
  state: () => ({ cobro: estadoInicial() }),
})
