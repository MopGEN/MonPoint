package com.monpoint.reportes.pdf;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.model.VentaProyeccion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Arma los cuatro PDF de ms-reportes (H-051, H-052, H-053) con iText 9. Solo da formato: no consulta datos
 * ni decide permisos; eso lo hace {@code DocumentoService}.
 * <p>
 * Decisiones:
 * <ul>
 *   <li>Se escribe a memoria ({@code ByteArrayOutputStream}): nunca se crean archivos en el contenedor.</li>
 *   <li>Fuentes estándar (Helvetica) con codificación WinAnsi: no hay archivos de fuente que empaquetar y cubren
 *       los acentos, la ñ y los signos ¿¡. Un carácter fuera de WinAnsi (p. ej. un emoji) no se dibuja.</li>
 *   <li>Toda fecha se muestra en la zona del negocio, igual que la agregación de la fase 3.</li>
 *   <li>Los importes nunca se redondean: se muestran con todos sus decimales significativos (mínimo dos).</li>
 * </ul>
 */
@Component
public class PdfGenerador {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SIN_DATO = "—";
    private static final String PUBLICO_EN_GENERAL = "Público en general";

    private final ZoneId zona;

    public PdfGenerador(@Value("${monpoint.reportes.zona-horaria}") ZoneId zona) {
        this.zona = zona;
    }

    // ------------------------------------------------------------------ documentos

    /** Comprobante de una venta confirmada (H-051): folio, fecha, cliente, líneas congeladas y total exacto. */
    public byte[] ticket(VentaProyeccion venta, Configuracion empresa, Instant generado) {
        return generar("Comprobante de venta", empresa, generado, (doc, f) -> {
            doc.add(dato(f, "Folio", String.valueOf(venta.getFolio())));
            doc.add(dato(f, "Fecha", fechaHora(venta.getFecha())));
            doc.add(dato(f, "Cliente", venta.getClienteNombre() == null ? PUBLICO_EN_GENERAL : venta.getClienteNombre())
                    .setMarginBottom(10));

            Table tabla = tabla(f, izquierda("Producto", 50), derecha("Cantidad", 14),
                    derecha("Precio unitario", 18), derecha("Importe", 18));
            for (LineaVenta linea : venta.getLineas()) {
                tabla.addCell(celda(f, linea.getNombreProducto(), TextAlignment.LEFT));
                tabla.addCell(celda(f, String.valueOf(linea.getCantidad()), TextAlignment.RIGHT));
                tabla.addCell(celda(f, moneda(linea.getPrecioUnitario()), TextAlignment.RIGHT));
                tabla.addCell(celda(f, moneda(linea.importe()), TextAlignment.RIGHT));
            }
            doc.add(tabla);
            // Se imprime el total de la venta tal como lo calculó ms-ventas, no una suma rehecha aquí.
            doc.add(resumen(f, "Total: " + moneda(venta.getTotal()), 13));
        });
    }

    /** Historial del periodo (H-052). Un periodo sin ventas también produce un documento válido que lo dice. */
    public byte[] historial(List<VentaProyeccion> ventas, LocalDate desde, LocalDate hasta, BigDecimal total,
                            Configuracion empresa, Instant generado) {
        return generar("Historial de ventas", empresa, generado, (doc, f) -> {
            doc.add(dato(f, "Periodo", FECHA.format(desde) + " al " + FECHA.format(hasta)).setMarginBottom(10));
            if (ventas.isEmpty()) {
                doc.add(aviso(f, "No hay ventas confirmadas en el periodo consultado."));
            } else {
                Table tabla = tabla(f, derecha("Folio", 12), izquierda("Fecha", 22), izquierda("Cliente", 46),
                        derecha("Total", 20));
                for (VentaProyeccion venta : ventas) {
                    tabla.addCell(celda(f, String.valueOf(venta.getFolio()), TextAlignment.RIGHT));
                    tabla.addCell(celda(f, fechaHora(venta.getFecha()), TextAlignment.LEFT));
                    tabla.addCell(celda(f, venta.getClienteNombre() == null
                            ? PUBLICO_EN_GENERAL : venta.getClienteNombre(), TextAlignment.LEFT));
                    tabla.addCell(celda(f, moneda(venta.getTotal()), TextAlignment.RIGHT));
                }
                doc.add(tabla);
            }
            doc.add(resumen(f, "Ventas: " + ventas.size(), 10));
            doc.add(resumen(f, "Total acumulado: " + moneda(total), 13).setMarginTop(2));
        });
    }

    /** Listado del catálogo vigente (H-053). Recibe solo productos activos. */
    public byte[] listadoProductos(List<ProductoProyeccion> productos, Configuracion empresa, Instant generado) {
        return generar("Listado de productos", empresa, generado, (doc, f) -> {
            if (productos.isEmpty()) {
                doc.add(aviso(f, "No hay productos activos."));
            } else {
                Table tabla = tabla(f, izquierda("Producto", 75), derecha("Precio", 25));
                for (ProductoProyeccion producto : productos) {
                    tabla.addCell(celda(f, producto.getNombre(), TextAlignment.LEFT));
                    tabla.addCell(celda(f, moneda(producto.getPrecio()), TextAlignment.RIGHT));
                }
                doc.add(tabla);
            }
            doc.add(resumen(f, "Total de registros: " + productos.size(), 10));
        });
    }

    /** Listado de clientes (H-053). */
    public byte[] listadoClientes(List<ContraparteProyeccion> clientes, Configuracion empresa, Instant generado) {
        return generar("Listado de clientes", empresa, generado, (doc, f) -> {
            if (clientes.isEmpty()) {
                doc.add(aviso(f, "No hay clientes registrados."));
            } else {
                Table tabla = tabla(f, izquierda("Nombre", 27), izquierda("Correo", 27), izquierda("Teléfono", 16),
                        izquierda("Dirección", 30));
                for (ContraparteProyeccion cliente : clientes) {
                    tabla.addCell(celda(f, cliente.getNombre(), TextAlignment.LEFT));
                    tabla.addCell(celda(f, cliente.getCorreo(), TextAlignment.LEFT));
                    tabla.addCell(celda(f, cliente.getTelefono(), TextAlignment.LEFT));
                    tabla.addCell(celda(f, cliente.getDireccion(), TextAlignment.LEFT));
                }
                doc.add(tabla);
            }
            doc.add(resumen(f, "Total de registros: " + clientes.size(), 10));
        });
    }

    // ------------------------------------------------------------------ formato compartido

    /**
     * Importe sin redondeo: mínimo dos decimales y, si el valor trae más, todos los significativos. Así el total
     * impreso coincide EXACTAMENTE con el guardado (H-051). Separadores como en México: 1,234.50.
     */
    static String moneda(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        formato.setMaximumFractionDigits(Math.max(2, valor.stripTrailingZeros().scale()));
        formato.setRoundingMode(RoundingMode.UNNECESSARY);
        return "$" + formato.format(valor);
    }

    private String fechaHora(Instant instante) {
        return FECHA_HORA.format(instante.atZone(zona));
    }

    @FunctionalInterface
    private interface Contenido {
        void escribir(Document doc, Fuentes fuentes);
    }

    /** Las fuentes de iText pertenecen a un solo documento: se crean de nuevo en cada PDF. */
    private record Fuentes(PdfFont normal, PdfFont negrita) {
    }

    /** Estructura común: encabezado de empresa, título, fecha de generación y el contenido de cada documento. */
    private byte[] generar(String titulo, Configuracion empresa, Instant generado, Contenido contenido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (Document doc = new Document(new PdfDocument(new PdfWriter(salida)), PageSize.LETTER)) {
            Fuentes fuentes = new Fuentes(PdfFontFactory.createFont(StandardFonts.HELVETICA),
                    PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD));
            doc.getPdfDocument().getDocumentInfo().setTitle(titulo);
            doc.setFont(fuentes.normal()).setFontSize(10);

            encabezado(doc, fuentes, empresa);
            doc.add(new Paragraph(titulo).setFont(fuentes.negrita()).setFontSize(15)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(12).setMarginBottom(0));
            doc.add(new Paragraph("Generado el " + fechaHora(generado) + " (" + zona.getId() + ")")
                    .setFontSize(8).setTextAlignment(TextAlignment.CENTER).setMarginBottom(14));
            contenido.escribir(doc, fuentes);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el PDF: " + titulo, ex);
        }
        return salida.toByteArray();
    }

    /** RF-5.6: los datos de la empresa encabezan todo documento; si faltan, se dice en lugar de inventarlos. */
    private static void encabezado(Document doc, Fuentes f, Configuracion empresa) {
        if (empresa == null) {
            doc.add(new Paragraph("Empresa sin configurar").setFont(f.negrita()).setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            doc.add(new Paragraph("Un administrador puede registrar los datos de la empresa en Configuración.")
                    .setFontSize(8).setTextAlignment(TextAlignment.CENTER));
            return;
        }
        doc.add(new Paragraph(empresa.getNombreEmpresa()).setFont(f.negrita()).setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
        if (empresa.getDireccion() != null) {
            doc.add(new Paragraph(empresa.getDireccion()).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(0).setMarginBottom(0));
        }
        String fiscal = "RFC: " + empresa.getRfc()
                + (empresa.getTelefono() == null ? "" : " · Tel: " + empresa.getTelefono());
        doc.add(new Paragraph(fiscal).setFontSize(9).setTextAlignment(TextAlignment.CENTER).setMarginTop(0));
    }

    private static Paragraph dato(Fuentes f, String etiqueta, String valor) {
        return new Paragraph().add(new Text(etiqueta + ": ").setFont(f.negrita())).add(new Text(valor))
                .setMarginTop(0).setMarginBottom(2);
    }

    /** Título, ancho relativo y alineación de una columna; los números van a la derecha para compararlos. */
    private record Columna(String titulo, float ancho, TextAlignment alineacion) {
    }

    private static Columna izquierda(String titulo, float ancho) {
        return new Columna(titulo, ancho, TextAlignment.LEFT);
    }

    private static Columna derecha(String titulo, float ancho) {
        return new Columna(titulo, ancho, TextAlignment.RIGHT);
    }

    /** Tabla a todo el ancho; el encabezado se repite si la tabla continúa en otra página. */
    private static Table tabla(Fuentes f, Columna... columnas) {
        float[] anchos = new float[columnas.length];
        for (int i = 0; i < columnas.length; i++) {
            anchos[i] = columnas[i].ancho();
        }
        Table tabla = new Table(UnitValue.createPercentArray(anchos)).useAllAvailableWidth();
        for (Columna columna : columnas) {
            tabla.addHeaderCell(new Cell().add(new Paragraph(columna.titulo()).setFont(f.negrita()))
                    .setTextAlignment(columna.alineacion()));
        }
        return tabla;
    }

    private static Cell celda(Fuentes f, String texto, TextAlignment alineacion) {
        return new Cell().add(new Paragraph(texto == null || texto.isBlank() ? SIN_DATO : texto).setFont(f.normal()))
                .setTextAlignment(alineacion);
    }

    private static Paragraph resumen(Fuentes f, String texto, float tamano) {
        return new Paragraph(texto).setFont(f.negrita()).setFontSize(tamano)
                .setTextAlignment(TextAlignment.RIGHT).setMarginTop(8).setMarginBottom(0);
    }

    private static Paragraph aviso(Fuentes f, String texto) {
        return new Paragraph(texto).setFontSize(11).setTextAlignment(TextAlignment.CENTER).setMarginTop(20);
    }
}
