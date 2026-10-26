# Mensaje para Victor

Victor, ya está implementado el consumidor de cierre de ventas de la 4.4.
Para completar el motivo comprensible de la 5.2, necesitamos ampliar
`StockRechazado.datos` con `disponible` y `solicitado`, ambos números enteros.

¿Puedes incluirlos al emitir `EXISTENCIA_INSUFICIENTE`? `solicitado` debe ser
la cantidad total agrupada por producto y `disponible` la existencia observada
al rechazar la reserva. No sería una promesa de stock actual: puede cambiar
después. Para un producto inexistente, propongo `disponible: null`.

El nombre y la cantidad solicitada que mostramos salen de las líneas congeladas
de la venta; ms-ventas no guarda ni consulta existencias. Con el dato adicional
podremos mostrar «existencia insuficiente de Tinta para sello, disponible 1,
solicitado 3». Los campos ya están previstos como Integer en el DTO de entrada,
por lo que el cambio puede ser aditivo. Coordinamos después el uso de disponible
en el mensaje y sus pruebas.

Mientras tanto funciona la frase sin disponible y sigue consumiéndose el contrato
actual. Avísame cuando esté acordado y fusionado para conectar ese dato en ventas.
