# Fuentes autoalojadas

`inter-latin.woff2` (48.256 bytes) e `inter-latin-ext.woff2` (85.068 bytes)
son los dos recursos WOFF2 de Inter extraídos sin modificar del `index.html`
del prototipo entregado por el usuario. Son fuentes variables normales,
pesos 100–900; base.css declara sus subconjuntos Unicode.

No se enlaza Google Fonts. Vite incluye ambos archivos en dist y el navegador
solicita los subconjuntos que necesita al mismo origen del cliente.

La licencia SIL Open Font License 1.1 se conserva en [OFL.txt](OFL.txt),
obtenida del [repositorio oficial de Inter](https://github.com/rsms/inter/blob/master/LICENSE.txt).
Los hashes de estos recursos y del logo están en `fuentes.json` de la evidencia
de la concha; su procedencia es el prototipo, no una reconstrucción del logo.

## IBM Plex Mono

`ibm-plex-mono-latin-400.woff2` (14.708 bytes) y
`ibm-plex-mono-latin-500.woff2` (14.888 bytes) son los subconjuntos latinos de
IBM Plex Mono regular y medium incluidos en el mismo HTML del prototipo.
Se extrajeron sin modificar los WOFF2. Se usan en la pila monoespaciada;
Caja presenta sus importes con Inter y cifras tabulares.

La licencia [IBM-Plex-Mono-OFL.txt](IBM-Plex-Mono-OFL.txt) procede de la
[distribución oficial en Google Fonts](https://github.com/google/fonts/blob/main/ofl/ibmplexmono/OFL.txt).
Los hashes de esta incorporación están en el expediente de Caja visual del
12 de noviembre de 2026.

main.js importa ambas licencias como assets y las enlaza con rel="license".
Así se incluyen también en dist y en la distribución nginx de las fuentes.
