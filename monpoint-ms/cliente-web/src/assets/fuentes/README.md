# Inter autoalojada

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
