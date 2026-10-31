import { defineStore } from 'pinia'

/**
 * Almacen de sesion.
 *
 * ════════════════════════════════════════════════════════════════════════════
 *  EL TOKEN VIVE SOLO EN MEMORIA. NO SE GUARDA EN localStorage NI EN
 *  sessionStorage NI EN UNA COOKIE LEGIBLE POR JAVASCRIPT.
 *
 *  Historias H-080 y H-081 del Documento 03. Cualquier JavaScript que llegue a
 *  ejecutarse en la pagina (una dependencia comprometida, una extension, una
 *  inyeccion) puede leer localStorage y sessionStorage completos. Un token
 *  guardado ahi se puede exfiltrar; uno que solo existe en una variable de este
 *  modulo desaparece al cerrar la pestana.
 *
 *  CONSECUENCIA ACEPTADA: recargar la pagina (F5) cierra la sesion. No es un
 *  defecto, es el costo de la decision. Si alguien la considera inaceptable, la
 *  solucion correcta NO es mover el token a localStorage, sino una cookie
 *  HttpOnly+Secure+SameSite emitida por el Gateway, que el navegador adjunta
 *  sola y JavaScript no puede leer. Eso implica proteccion anti-CSRF y esta
 *  fuera del alcance de v3.
 * ════════════════════════════════════════════════════════════════════════════
 */

import { gateway } from '@/api/gateway'

export const ROL_ADMIN = 'ADMIN'
export const ROL_VENDEDOR = 'VENDEDOR'

export const useSesion = defineStore('sesion', {
  state: () => ({
    token: null,
    /** Instante absoluto de caducidad en milisegundos de epoca, o null. */
    caducaEn: null,
    usuario: null, // { correo, nombre, rol }
    entrando: false,
  }),

  getters: {
    autenticado: (s) => s.token !== null,
    rol: (s) => s.usuario?.rol ?? null,
    esAdmin: (s) => s.usuario?.rol === ROL_ADMIN,
    esVendedor: (s) => s.usuario?.rol === ROL_VENDEDOR,
    nombre: (s) => s.usuario?.nombre ?? '',

    /**
     * Caducidad conocida por el cliente. Es una cortesia para evitar una ida al
     * servidor que ya se sabe perdida; la autoridad sobre la validez del token
     * es el Gateway, porque el reloj del navegador puede estar desajustado.
     */
    caducado: (s) => s.caducaEn !== null && Date.now() >= s.caducaEn,
  },

  actions: {
    /**
     * @param {string} correo
     * @param {string} contrasena
     * @throws {import('@/api/gateway').ErrorDeGateway} con el mensaje listo
     *   para mostrarse. Un 401 aqui es contrasena incorrecta.
     */
    async iniciar (correo, contrasena) {
      this.entrando = true
      try {
        const r = await gateway.enviar(
          '/auth/login',
          { correo, contrasena },
          { sinAutenticar: true },
        )

        // ───────────────────────────────────────────────────────────────────
        // `expiraEn` es la VIGENCIA EN MILISEGUNDOS, no una fecha.
        // ms-auth devuelve, por ejemplo, 86400000 para 24 horas. Tratarlo como
        // instante de epoca daria el 2 de enero de 1970 y la sesion se veria
        // caducada al nacer.
        // ───────────────────────────────────────────────────────────────────
        const vigencia = Number(r?.expiraEn)
        this.caducaEn = Number.isFinite(vigencia) && vigencia > 0
          ? Date.now() + vigencia
          : null

        this.token = r.token
        this.usuario = { correo: r.correo, nombre: r.nombre, rol: r.rol }
        return this.usuario
      } finally {
        this.entrando = false
      }
    },

    /** Borra todo rastro de la sesion. Es sincrono y no falla. */
    cerrar () {
      this.token = null
      this.caducaEn = null
      this.usuario = null
    },
  },
})
