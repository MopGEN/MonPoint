/** Guarda los bytes ya recibidos; el nombre lo pone el cliente por CORS. */
export function guardarDocumento (blob, nombre) {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  try {
    enlace.href = url
    enlace.download = nombre
    enlace.rel = 'noopener'
    document.body.appendChild(enlace)
    enlace.click()
  } finally {
    enlace.remove()
    // Revocar de inmediato puede cancelar la descarga recien iniciada.
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  }
}
