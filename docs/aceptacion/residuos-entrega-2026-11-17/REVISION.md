# Retoques tras la revisión independiente — 17 de noviembre de 2026

La revisión del PO reprodujo las quince consultas nginx con la regla corregida
y el fallo esperado de la configuración base en nginx 1.24.0. Ese resultado
corrobora la regla, no sustituye la ejecución 1.28.3 del expediente original.
No se repitió nginx en esta revisión: su configuración no cambió.

Se incorporaron los tres retoques sobre `61883f5`:

- El helper HTTP devuelve `{ estado, datos }`, sin un centinela de empresa.
  El bloque de configuración interpreta su 404. Un estado no exitoso admitido
  conserva el código y omite el cuerpo, sin atribuirle significado de negocio.
  Un 200 null no permite crear empresa; se conserva ese caso discriminante.
- El procedimiento Windows parte de la raíz del clon y no publica una ruta
  con nombre de usuario. No se modificaron rutas de expedientes históricos.
- El diagnóstico de la línea JWT_SECRET menciona duplicados y codificación,
  y la nota explica ReadAllText, BOM y UTF-16 sin BOM. No se ejecutó el bloque
  de PowerShell ni se rotó una clave para verificar esta corrección editorial.

Después del cambio del helper se ejecutaron de nuevo las **54 pruebas Node**:
[registro](revision-guiones-node.txt) y [fuentes](revision-fuentes.json).
Pasan sin fallos ni omitidas. Las pruebas usan HTTP simulado; no certifican
la empresa ni la rotación de la instalación Windows.

Los archivos de la ejecución original, incluido fuentes-probadas.json, quedan
intactos y describen el código probado antes de estos retoques. Los hashes de
fuentes de esta revisión se conservan aparte. La comprobación documental revisó
referencias locales y la ausencia de la ruta personal en el procedimiento.
