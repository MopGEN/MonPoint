package utils;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import entidades.Cliente;
import entidades.Configuracion;
import entidades.DetalleVenta;
import entidades.Producto;
import entidades.Venta;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PDFUtil {

    private static final float[] COLUMNAS_VENTAS = {40F, 150F, 100F, 100F};
    private static final float[] COLUMNAS_CLIENTES = {40F, 120F, 150F, 100F, 150F};
    private static final float[] COLUMNAS_PRODUCTOS = {40F, 140F, 80F, 60F, 180F};
    private static final float[] COLUMNAS_TICKET = {50F, 200F, 80F, 80F};

    private static PdfFont getFontBold() throws IOException {
        return PdfFontFactory.createFont("Helvetica-Bold");
    }

    private static PdfFont getFontNormal() throws IOException {
        return PdfFontFactory.createFont("Helvetica");
    }

    private static void agregarEncabezadoYFooter(Document doc, String tituloDocumento) throws IOException {
        PdfFont fontBold = getFontBold();
        PdfFont fontNormal = getFontNormal();

        Configuracion config = ConfiguracionUtil.obtenerConfiguracion();

        Paragraph empresa = new Paragraph(config != null ? config.getNombreEmpresa() : "Empresa")
                .setFont(fontBold)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER);
        doc.add(empresa);

        if (config != null) {
            doc.add(new Paragraph(config.getDireccion())
                    .setFont(fontNormal)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("RFC: " + config.getRfc() + " | Tel: " + config.getTelefono())
                    .setFont(fontNormal)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        doc.add(new Paragraph(tituloDocumento)
                .setFont(fontBold)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10)
                .setMarginBottom(20));
    }

    private static void agregarFooter(Document doc) throws IOException {
        PdfFont fontNormal = getFontNormal();
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph footer = new Paragraph("Generado el " + fecha)
                .setFont(fontNormal)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(30);
        doc.add(footer);
    }

    public static void exportarVenta(File destino, Venta venta) throws IOException {
        PdfWriter writer = new PdfWriter(destino);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        PdfFont fontNormal = getFontNormal();
        PdfFont fontBold = getFontBold();

        agregarEncabezadoYFooter(doc, "🧾 Ticket de Venta");

        doc.add(new Paragraph("Fecha: " + venta.getFecha()).setFont(fontNormal));
        doc.add(new Paragraph("Cliente: " + venta.getNombreCliente()).setFont(fontNormal));
        doc.add(new Paragraph(" ").setMarginBottom(10));

        Table tabla = crearTabla(COLUMNAS_TICKET, new String[]{"ID", "Producto", "Cantidad", "Precio"}, fontBold);

        for (DetalleVenta detalle : venta.getDetalleVentas()) {
            tabla.addCell(new Cell().add(new Paragraph(String.valueOf(detalle.getId())).setFont(fontNormal)));
            tabla.addCell(new Cell().add(new Paragraph(detalle.getProducto().getNombre()).setFont(fontNormal)));
            tabla.addCell(new Cell().add(new Paragraph(String.valueOf(detalle.getCantidad())).setFont(fontNormal)));
            tabla.addCell(new Cell().add(new Paragraph("$" + String.format("%.2f", detalle.getPrecioUnitario())).setFont(fontNormal)));
        }

        doc.add(tabla);

        doc.add(new Paragraph("Total: $" + String.format("%.2f", venta.getTotal()))
                .setFont(fontBold)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(10));

        agregarFooter(doc);
        doc.close();
    }

    public static void exportarHistorialVentas(File destino, List<Venta> ventas) throws IOException {
        PdfWriter writer = new PdfWriter(destino);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        PdfFont fontNormal = getFontNormal();
        PdfFont fontBold = getFontBold();

        agregarEncabezadoYFooter(doc, "📈 Historial de Ventas");

        Table tabla = crearTabla(COLUMNAS_VENTAS, new String[]{"ID", "Cliente", "Fecha", "Total"}, fontBold);

        for (Venta venta : ventas) {
            tabla.addCell(crearCelda(String.valueOf(venta.getId()), fontNormal));
            tabla.addCell(crearCelda(venta.getNombreCliente(), fontNormal));
            tabla.addCell(crearCelda(venta.getFecha().toString(), fontNormal));
            tabla.addCell(crearCelda("$" + String.format("%.2f", venta.getTotal()), fontNormal));
        }

        doc.add(tabla);
        agregarFooter(doc);
        doc.close();
    }

    public static void exportarClientes(File destino, List<Cliente> clientes) throws IOException {
        PdfWriter writer = new PdfWriter(destino);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        PdfFont fontNormal = getFontNormal();
        PdfFont fontBold = getFontBold();

        agregarEncabezadoYFooter(doc, "📄 Listado de Clientes");

        Table tabla = crearTabla(COLUMNAS_CLIENTES, new String[]{"ID", "Nombre", "Correo", "Teléfono", "Dirección"}, fontBold);

        for (Cliente cliente : clientes) {
            tabla.addCell(crearCelda(String.valueOf(cliente.getId()), fontNormal));
            tabla.addCell(crearCelda(cliente.getNombre(), fontNormal));
            tabla.addCell(crearCelda(cliente.getCorreo(), fontNormal));
            tabla.addCell(crearCelda(cliente.getTelefono(), fontNormal));
            tabla.addCell(crearCelda(cliente.getDireccion(), fontNormal));
        }

        doc.add(tabla);
        agregarFooter(doc);
        doc.close();
    }

    public static void exportarProductos(File destino, ObservableList<Producto> productos) throws IOException {
        PdfWriter writer = new PdfWriter(destino);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        PdfFont fontNormal = getFontNormal();
        PdfFont fontBold = getFontBold();

        agregarEncabezadoYFooter(doc, "📦 Listado de Productos");

        Table tabla = crearTabla(COLUMNAS_PRODUCTOS, new String[]{"ID", "Nombre", "Precio", "Stock", "Descripción"}, fontBold);

        for (Producto p : productos) {
            tabla.addCell(crearCelda(String.valueOf(p.getId()), fontNormal));
            tabla.addCell(crearCelda(p.getNombre(), fontNormal));
            tabla.addCell(crearCelda("$" + String.format("%.2f", p.getPrecio()), fontNormal));
            tabla.addCell(crearCelda(String.valueOf(p.getStock()), fontNormal));
            tabla.addCell(crearCelda(p.getDescripcion(), fontNormal));
        }

        doc.add(tabla);
        agregarFooter(doc);
        doc.close();
    }

    private static Table crearTabla(float[] columnas, String[] headers, PdfFont fontBold) {
        Table tabla = new Table(columnas).setHorizontalAlignment(HorizontalAlignment.CENTER);
        for (String header : headers) {
            tabla.addHeaderCell(new Cell().add(new Paragraph(header)
                    .setFont(fontBold)
                    .setFontSize(11)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)));
        }
        return tabla;
    }

    private static Cell crearCelda(String texto, PdfFont fontNormal) {
        return new Cell().add(new Paragraph(texto)
                .setFont(fontNormal)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER));
    }
}
