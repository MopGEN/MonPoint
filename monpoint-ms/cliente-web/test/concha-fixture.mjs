// Lecturas adicionales de la cabecera. Registrar antes de los dobles propios
// de cada pantalla para que aquellos conserven prioridad. simularConcha no se
// llama en los recorridos reales; estos solo reutilizan los helpers de menu.
export async function simularConcha (pagina, api = 'http://localhost:8080') {
  await pagina.route(`${api}/reportes/configuracion`, (r) => r.fulfill({
    status: 404, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Empresa sin registrar' }),
  }))
  await pagina.route(`${api}/notificaciones?leida=false`, (r) => r.fulfill({
    status: 200, contentType: 'application/json', body: '[]',
  }))
}

export const menuUsuario = (pagina) => pagina.getByRole('button', { name: /^Menú de usuario:/ })
export async function salirDesdeMenu (pagina) {
  const boton = menuUsuario(pagina)
  if (await boton.getAttribute('aria-expanded') !== 'true') await boton.click()
  await pagina.getByRole('button', { name: 'Salir', exact: true }).click()
}
