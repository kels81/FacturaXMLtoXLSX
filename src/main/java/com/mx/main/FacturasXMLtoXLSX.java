package com.mx.main;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import com.mx.bean.CFDI;
import com.mx.utils.Constantes;
import com.mx.utils.FormaPago;
import com.mx.utils.XmlNode;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FacturasXMLtoXLSX {

    private static final Set<String> SPECIAL_COLUMNS = Set.of("BASE", "SUB TOTAL", "IVA");
    private static final String ZERO = "0";
    private static final Logger LOGGER = Logger.getLogger("newexcel.ExcelOOXML");
    private static final Map<String, String> TRASLADADOS = new HashMap<>();
    private static String LABEL;
    private static String DIRECTORY;
    private static final String[] COLUMNS_HEADERS = {"XML", "Metodo\nPago", "Uso\nCFDI", "Forma\nPago", "Tipo\nComprobante", "Tipo\nFactor", "RFC\nEmisor", "Nombre\nEmisor", "SUB TOTAL", "Total\nImpuesto Trasladado", "Total", "Traslado\nIVA: 16", "Descuento", "Base IVA 0%", "BASE", "IVA", "TOTAL",
            "Estatus\nPago", "Fecha\nPago" };

    private static final DateTimeFormatter FORMATTER_IN = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATTER_OUT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", new Locale("es", "MX"));

    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();
    static {
        try {
            FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.SEVERE, "Error configurando FACTORY", e);
        }
    }

    // ── REGLAS DEL SAT PARA IVA ACREDITABLE ──
    private static final Set<String> USOS_SAT = Set.of("G01", "G03");
    private static final Set<String> FORMAS_PAGO_EXCLUIDAS = Set.of("01", "31");
    // Mapa para heredar el Uso CFDI de las facturas PPD a sus Complementos de Pago
    private static final Map<String, String> UUID_TO_USO_CFDI = new HashMap<>();

    public static void main(String[] args) {
        int noMes = 3; // Febrero
        DIRECTORY = Constantes.getDirectoryForMonth(noMes);
        LABEL = Constantes.getMonthName(noMes).toUpperCase();

        List<CFDI> filesCFDI = cfdiFile(new File(DIRECTORY), noMes);

        // Ordenar la lista por RFC del emisor
        filesCFDI.sort(Comparator.comparing(CFDI::getRfcEmisor));

        createXLSX(filesCFDI, noMes);
        LOGGER.log(Level.INFO, "Total de Facturas procesadas: {0}", filesCFDI.size());
    }

    // =========================================================================
    // LECTURA DE ARCHIVOS XML
    // =========================================================================

    private static List<CFDI> cfdiFile(File directory, int mesActual) {
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.log(Level.SEVERE, "Directorio no válido: {0}", directory.getPath());
            return Collections.emptyList();
        }
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            LOGGER.log(Level.WARNING, "Directorio vacío: {0}", directory.getPath());
            return Collections.emptyList();
        }

        List<CFDI> lista = Arrays.stream(files)
                .filter(file -> {
                    String ext = FilenameUtils.getExtension(file.getPath());
                    return !ext.equalsIgnoreCase("zip") && !ext.equalsIgnoreCase("xlsx");
                })
                .map(FacturasXMLtoXLSX::processFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Cruzar complementos con facturas PPD de este mes y meses anteriores
        vincularComplementosDePago(lista, mesActual);

        return lista;
    }

    private static void vincularComplementosDePago(List<CFDI> lista, int mesActual) {
        Map<String, CFDI> porUUID = lista.stream()
                .filter(c -> c.getUuidPropio() != null)
                .collect(Collectors.toMap(
                        CFDI::getUuidPropio, c -> c, (a, b) -> a
                ));

        Map<String, CFDI> porUUIDHistorico = cargarUUIDsHistoricos(porUUID, mesActual);
        Map<String, CFDI> facturasHistoricasAgregar = new LinkedHashMap<>();

        lista.stream()
                .filter(c -> "P".equals(c.getTipoDeComprobante()))
                .filter(c -> c.getUuidReferencia() != null)
                .forEach(complemento -> {
                    Arrays.stream(complemento.getUuidReferencia().split(","))
                            .map(String::trim)
                            .forEach(uuid -> {
                                CFDI factura = porUUID.get(uuid);
                                boolean esHistorica = false;

                                if (factura == null) {
                                    factura = porUUIDHistorico.get(uuid);
                                    esHistorica = factura != null;
                                }

                                if (factura != null) {
                                    String refActual = factura.getUuidReferencia();
                                    String nuevaRef = "COMPLEMENTO:" + complemento.getNombreArchivo();

                                    factura.setUuidReferencia(
                                            (refActual == null || refActual.isBlank())
                                                    ? nuevaRef
                                                    : refActual + ", " + nuevaRef
                                    );

                                    if (esHistorica) {
                                        facturasHistoricasAgregar.put(uuid, factura);
                                    }
                                } else {
                                    LOGGER.log(Level.WARNING,
                                            "⚠ Factura origen no encontrada para complemento: {0} UUID: {1}",
                                            new Object[]{complemento.getNombreEmisor(), uuid});
                                }
                            });
                });

        lista.addAll(facturasHistoricasAgregar.values());
    }

    private static Map<String, CFDI> cargarUUIDsHistoricos(Map<String, CFDI> yaIndexados, int mesActual) {
        Map<String, CFDI> historico = new HashMap<>();

        for (int mes = 1; mes < mesActual; mes++) {
            String dirMes;
            try {
                dirMes = Constantes.getHistoricDirectoryForMonth(mes);
            } catch (IllegalArgumentException e) {
                continue;
            }

            File carpeta = new File(dirMes);
            if (!carpeta.exists() || !carpeta.isDirectory()) continue;

            File[] archivos = carpeta.listFiles();
            if (archivos == null || archivos.length == 0) continue;

            for (File archivo : archivos) {
                String ext = FilenameUtils.getExtension(archivo.getPath());
                if (ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("xlsx")) continue;

                CFDI cfdi = processFile(archivo);
                if (cfdi == null || cfdi.getUuidPropio() == null) continue;

                if (!yaIndexados.containsKey(cfdi.getUuidPropio())) {
                    historico.put(cfdi.getUuidPropio(), cfdi);
                }
            }
        }
        return historico;
    }

    private static CFDI processFile(File xmlFile) {
        Document xmlDocument = getDocument(xmlFile);
        if (xmlDocument == null) {
            LOGGER.log(Level.WARNING, "Archivo omitido por error de lectura: {0}", xmlFile.getName());
            return null;
        }

        xmlDocument.getDocumentElement().normalize();
        CFDI cfdi = new CFDI();
        cfdi.setNombreArchivo(xmlFile.getName());
        exploreNodes(xmlDocument.getDocumentElement(), cfdi);

        // Guardar el UUID y su UsoCFDI para validaciones cruzadas (SAT)
        if (cfdi.getUuidPropio() != null && cfdi.getUsoCFDI() != null) {
            UUID_TO_USO_CFDI.put(cfdi.getUuidPropio(), cfdi.getUsoCFDI());
        }

        return cfdi;
    }

    private static Document getDocument(File xmlFile) {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            return builder.parse(xmlFile);
        } catch (ParserConfigurationException | IOException | org.xml.sax.SAXException e) {
            return null;
        }
    }

    // =========================================================================
    // RECORRIDO Y MAPEO DEL XML
    // =========================================================================

    private static void exploreNodes(Node node, CFDI cfdi) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            XmlNode xmlNode = XmlNode.fromNodeName(element.getNodeName());

            if (xmlNode != null) {
                for (String attr : xmlNode.getAttributes()) {
                    String value = element.getAttribute(attr);
                    if (!value.isEmpty()) {
                        mapToCFDI(cfdi, xmlNode, attr, value);
                    }
                }

                if (xmlNode == XmlNode.CFDI_TRASLADO || xmlNode == XmlNode.PAGO20_TRASLADOP) {
                    if (xmlNode == XmlNode.PAGO20_TRASLADOP) {
                        workWithTrasladados(cfdi);
                    } else {
                        Node parent = node.getParentNode();
                        Node grandParent = (parent != null) ? parent.getParentNode() : null;
                        Node greatGrandParent = (grandParent != null) ? grandParent.getParentNode() : null;

                        if (greatGrandParent != null && !"cfdi:Concepto".equals(greatGrandParent.getNodeName())) {
                            workWithTrasladados(cfdi);
                        } else {
                            TRASLADADOS.clear();
                        }
                    }
                }
            }
        }

        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            exploreNodes(nodeList.item(i), cfdi);
        }
    }

    private static void mapToCFDI(CFDI cfdi, XmlNode node, String attr, String value) {
        switch (node) {
            case CFDI_COMPROBANTE:
                switch (attr) {
                    case "Fecha":             cfdi.setFecha(value);             break;
                    case "LugarExpedicion":   cfdi.setLugarExpedicion(value);   break;
                    case "Moneda":            cfdi.setMoneda(value);            break;
                    case "FormaPago":         cfdi.setFormaDePago(value);       break;
                    case "MetodoPago":        cfdi.setMetodoDePago(value);      break;
                    case "TipoDeComprobante": cfdi.setTipoDeComprobante(value); break;
                    case "SubTotal":          cfdi.setSubTotal(value);          break;
                    case "Descuento":         cfdi.setDescuento(value);         break;
                    case "Total":             cfdi.setTotal(value);             break;
                }
                break;
            case CFDI_EMISOR:
                switch (attr) {
                    case "Rfc":    cfdi.setRfcEmisor(value);    break;
                    case "Nombre": cfdi.setNombreEmisor(value); break;
                }
                break;
            case CFDI_RECEPTOR:
                switch (attr) {
                    case "Rfc":      cfdi.setRfcReceptor(value);    break;
                    case "Nombre":   cfdi.setNombreReceptor(value); break;
                    case "UsoCFDI":  cfdi.setUsoCFDI(value);        break;
                }
                break;
            case CFDI_TRASLADO:
            case PAGO20_TRASLADOP:
                String key = attr.endsWith("P") ? attr.substring(0, attr.length() - 1) : attr;
                TRASLADADOS.put(key, value);
                break;
            case PAGO20_PAGO:
                if ("FechaPago".equals(attr)) cfdi.setFechaPago(value);
                // Extraer forma de pago exclusiva del complemento
                if ("FormaDePagoP".equals(attr)) cfdi.setFormaDePago(value);
                break;
            case CFDI_IMPUESTOS:
                if ("TotalImpuestosTrasladados".equals(attr)) cfdi.setTotalImpuestoTrasladados(value);
                break;
            case TFD_TIMBREFISCALDIGITAL:
                if ("UUID".equals(attr)) cfdi.setUuidPropio(value.toUpperCase());
                break;
            case PAGO20_DOCTO:
            case PAGO10_DOCTO:
                if ("IdDocumento".equals(attr)) {
                    String actual = cfdi.getUuidReferencia();
                    cfdi.setUuidReferencia(
                            (actual == null || actual.isBlank()) ? value.toUpperCase() : actual + "," + value.toUpperCase()
                    );
                }
                break;
        }
    }

    // =========================================================================
    // LÓGICA CONTABLE DE TRASLADOS
    // =========================================================================

    private static void workWithTrasladados(CFDI cfdi) {
        String impuesto  = Optional.ofNullable(TRASLADADOS.get("Impuesto")).orElse(TRASLADADOS.get("impuesto"));
        String tasa      = Optional.ofNullable(TRASLADADOS.get("TasaOCuota")).orElse(TRASLADADOS.get("tasa"));
        String importe   = Optional.ofNullable(TRASLADADOS.get("Importe")).orElse(TRASLADADOS.get("importe"));
        String base      = Optional.ofNullable(TRASLADADOS.get("Base")).orElse(TRASLADADOS.get("base"));
        String tipoFactor= Optional.ofNullable(TRASLADADOS.get("TipoFactor")).orElse(TRASLADADOS.get("tipofactor"));

        if (importe == null || importe.isEmpty()) importe = ZERO;
        if (base    == null || base.isEmpty())    base    = ZERO;
        if (tipoFactor != null) cfdi.setTipoFactor(tipoFactor);

        boolean esIngresoPPD = "I".equals(cfdi.getTipoDeComprobante()) &&
                ("PPD".equals(cfdi.getMetodoDePago()) || "99".equals(cfdi.getFormaDePago()));

        if (esIngresoPPD) {
            cfdi.setTrasladoIVAOriginal(importe);
            importe = ZERO;
            base    = ZERO;
        }

        if (impuesto != null && (impuesto.equals("IVA") || impuesto.equals("002"))) {
            if ("0.000000".equals(tasa)) {
                double baseActual = Double.parseDouble(cfdi.getBaseIVA0() != null ? cfdi.getBaseIVA0() : "0");
                double baseNueva  = Double.parseDouble(base);
                cfdi.setBaseIVA0(String.valueOf(baseActual + baseNueva));
            } else {
                cfdi.setTrasladoIVA(importe);
                cfdi.setTasaIVA(tasa);
            }
        } else {
            cfdi.setTrasladoIEPS(importe);
            cfdi.setTasaIEPS(tasa);
        }
        TRASLADADOS.clear();
    }

    // =========================================================================
    // CREACIÓN DEL XLSX
    // =========================================================================

    private static void createXLSX(List<CFDI> listAllCFDI, int mesProceso) {
        try (Workbook workbook = new XSSFWorkbook()) {

            crearHojaResumen(workbook, listAllCFDI, mesProceso);

            // Hoja 1 — General
            Sheet pagina1 = workbook.createSheet(LABEL);
            createHeaderRow(pagina1, workbook);
            fillDataRows(pagina1, listAllCFDI, workbook);
            createTotalRow(pagina1, listAllCFDI.size(), workbook);

            // Hoja 2 — Facturas PUE (Solo Ingresos PUE permitidos por el SAT y bancarizados)
            List<CFDI> lstCFDITipoIngreso = listAllCFDI.stream()
                    .filter(c -> "I".equals(c.getTipoDeComprobante())
                            && "PUE".equals(c.getMetodoDePago())
                            && USOS_SAT.contains(c.getUsoCFDI())
                            && (c.getFormaDePago() == null || !FORMAS_PAGO_EXCLUIDAS.contains(c.getFormaDePago())))
                    .collect(Collectors.toList());
            Sheet pagina2 = workbook.createSheet("PUE");
            createHeaderRow(pagina2, workbook);
            fillDataRows(pagina2, lstCFDITipoIngreso, workbook);
            createTotalRow(pagina2, lstCFDITipoIngreso.size(), workbook);

            // Hoja 3 — Facturas PPD (Solo Ingresos PPD permitidos por el SAT)
            List<CFDI> lstCFDITipoPago = listAllCFDI.stream()
                    .filter(c -> "I".equals(c.getTipoDeComprobante())
                            && ("PPD".equals(c.getMetodoDePago()) || "99".equals(c.getFormaDePago()))
                            && USOS_SAT.contains(c.getUsoCFDI()))
                    .collect(Collectors.toList());
            Sheet pagina3 = workbook.createSheet("PPD");
            createHeaderRow(pagina3, workbook);
            fillDataRows(pagina3, lstCFDITipoPago, workbook);
            createTotalRow(pagina3, lstCFDITipoPago.size(), workbook);

            // Hoja 4 — Complementos CP01 (Estrictamente Tipo P, mes de pago correcto y bancarizados)
            List<CFDI> lstCFDITipoPagoDoctoRelacionado = listAllCFDI.stream()
                    .filter(c -> "P".equals(c.getTipoDeComprobante())
                            && "CP01".equals(c.getUsoCFDI())
                            && (c.getFechaPago() != null && extraerMes(c.getFechaPago()) == mesProceso)
                            && (c.getFormaDePago() == null || !FORMAS_PAGO_EXCLUIDAS.contains(c.getFormaDePago())))
                    .collect(Collectors.toList());
            Sheet pagina4 = workbook.createSheet("CP01");
            createHeaderRow(pagina4, workbook);
            fillDataRows(pagina4, lstCFDITipoPagoDoctoRelacionado, workbook);
            createTotalRow(pagina4, lstCFDITipoPagoDoctoRelacionado.size(), workbook);

            guardarArchivoXLSX(workbook);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al crear el archivo XLSX", e);
        }
    }

    private static void crearHojaResumen(Workbook workbook, List<CFDI> listAllCFDI, int mesProceso) {
        Sheet resumen = workbook.createSheet("RESUMEN");

        // [Estilos de celda...]
        CellStyle estTitulo = workbook.createCellStyle();
        Font fTitulo = workbook.createFont(); fTitulo.setBold(true); fTitulo.setFontHeightInPoints((short) 14); fTitulo.setColor(IndexedColors.WHITE.getIndex()); estTitulo.setFont(fTitulo); estTitulo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); estTitulo.setFillPattern(FillPatternType.SOLID_FOREGROUND); estTitulo.setAlignment(HorizontalAlignment.CENTER);
        CellStyle estHeader = workbook.createCellStyle(); Font fHeader = workbook.createFont(); fHeader.setBold(true); fHeader.setColor(IndexedColors.WHITE.getIndex()); estHeader.setFont(fHeader); estHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); estHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND); estHeader.setAlignment(HorizontalAlignment.CENTER); estHeader.setBorderBottom(BorderStyle.THIN); estHeader.setBorderTop(BorderStyle.THIN); estHeader.setBorderLeft(BorderStyle.THIN); estHeader.setBorderRight(BorderStyle.THIN);
        CellStyle estSubHeader = workbook.createCellStyle(); Font fSubHeader = workbook.createFont(); fSubHeader.setBold(true); fSubHeader.setColor(IndexedColors.WHITE.getIndex()); estSubHeader.setFont(fSubHeader); estSubHeader.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex()); estSubHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND); estSubHeader.setAlignment(HorizontalAlignment.CENTER); estSubHeader.setBorderBottom(BorderStyle.THIN); estSubHeader.setBorderTop(BorderStyle.THIN); estSubHeader.setBorderLeft(BorderStyle.THIN); estSubHeader.setBorderRight(BorderStyle.THIN);
        CellStyle estConcepto = workbook.createCellStyle(); Font fConcepto = workbook.createFont(); fConcepto.setBold(true); estConcepto.setFont(fConcepto); estConcepto.setBorderBottom(BorderStyle.THIN); estConcepto.setBorderTop(BorderStyle.THIN); estConcepto.setBorderLeft(BorderStyle.THIN); estConcepto.setBorderRight(BorderStyle.THIN);
        CellStyle estMoneda = workbook.createCellStyle(); estMoneda.setDataFormat((short) 8); estMoneda.setBorderBottom(BorderStyle.THIN); estMoneda.setBorderTop(BorderStyle.THIN); estMoneda.setBorderLeft(BorderStyle.THIN); estMoneda.setBorderRight(BorderStyle.THIN);
        CellStyle estTotalAcreditable = workbook.createCellStyle(); Font fTotal = workbook.createFont(); fTotal.setBold(true); fTotal.setColor(IndexedColors.WHITE.getIndex()); estTotalAcreditable.setFont(fTotal); estTotalAcreditable.setDataFormat((short) 8); estTotalAcreditable.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex()); estTotalAcreditable.setFillPattern(FillPatternType.SOLID_FOREGROUND); estTotalAcreditable.setBorderBottom(BorderStyle.THIN); estTotalAcreditable.setBorderTop(BorderStyle.THIN); estTotalAcreditable.setBorderLeft(BorderStyle.THIN); estTotalAcreditable.setBorderRight(BorderStyle.THIN);
        CellStyle estTotalBloqueado = workbook.createCellStyle(); estTotalBloqueado.setFont(fTotal); estTotalBloqueado.setDataFormat((short) 8); estTotalBloqueado.setFillForegroundColor(IndexedColors.DARK_RED.getIndex()); estTotalBloqueado.setFillPattern(FillPatternType.SOLID_FOREGROUND); estTotalBloqueado.setBorderBottom(BorderStyle.THIN); estTotalBloqueado.setBorderTop(BorderStyle.THIN); estTotalBloqueado.setBorderLeft(BorderStyle.THIN); estTotalBloqueado.setBorderRight(BorderStyle.THIN);
        CellStyle estNaranja = workbook.createCellStyle(); estNaranja.setDataFormat((short) 8); estNaranja.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex()); estNaranja.setFillPattern(FillPatternType.SOLID_FOREGROUND); estNaranja.setBorderBottom(BorderStyle.THIN); estNaranja.setBorderTop(BorderStyle.THIN); estNaranja.setBorderLeft(BorderStyle.THIN); estNaranja.setBorderRight(BorderStyle.THIN);
        CellStyle estNaranjaBold = workbook.createCellStyle(); Font fNaranjaBold = workbook.createFont(); fNaranjaBold.setBold(true); estNaranjaBold.setFont(fNaranjaBold); estNaranjaBold.setDataFormat((short) 8); estNaranjaBold.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex()); estNaranjaBold.setFillPattern(FillPatternType.SOLID_FOREGROUND); estNaranjaBold.setBorderBottom(BorderStyle.THIN); estNaranjaBold.setBorderTop(BorderStyle.THIN); estNaranjaBold.setBorderLeft(BorderStyle.THIN); estNaranjaBold.setBorderRight(BorderStyle.THIN);
        CellStyle estVerde = workbook.createCellStyle(); estVerde.setDataFormat((short) 8); estVerde.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex()); estVerde.setFillPattern(FillPatternType.SOLID_FOREGROUND); estVerde.setBorderBottom(BorderStyle.THIN); estVerde.setBorderTop(BorderStyle.THIN); estVerde.setBorderLeft(BorderStyle.THIN); estVerde.setBorderRight(BorderStyle.THIN);
        CellStyle estGris = workbook.createCellStyle(); Font fGris = workbook.createFont(); fGris.setItalic(true); fGris.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); estGris.setFont(fGris); estGris.setDataFormat((short) 8); estGris.setBorderBottom(BorderStyle.THIN); estGris.setBorderTop(BorderStyle.THIN); estGris.setBorderLeft(BorderStyle.THIN); estGris.setBorderRight(BorderStyle.THIN);
        CellStyle estGrisLabel = workbook.createCellStyle(); estGrisLabel.setFont(fGris); estGrisLabel.setBorderBottom(BorderStyle.THIN); estGrisLabel.setBorderTop(BorderStyle.THIN); estGrisLabel.setBorderLeft(BorderStyle.THIN); estGrisLabel.setBorderRight(BorderStyle.THIN);

        resumen.setColumnWidth(0, 4000);
        resumen.setColumnWidth(1, 14000);
        resumen.setColumnWidth(2, 5000);
        resumen.setColumnWidth(3, 5000);
        resumen.setColumnWidth(4, 5000);
        resumen.setColumnWidth(5, 5000);

        double pueSubTotal = 0, pueBase = 0, pueIVA = 0;
        double excluidosSubTotal = 0, excluidosBase = 0, excluidosIVA = 0;
        double ignoradosUsoSubTotal = 0, ignoradosUsoBase = 0, ignoradosUsoIVA = 0;
        double cp01SubTotal = 0, cp01Base = 0, cp01IVA = 0;
        double ppdSubTotal = 0, ppdBase = 0, ppdIVA = 0;
        double descuentos = 0;

        List<CFDI> cp01DelMes = new ArrayList<>();
        List<CFDI> ppdSinComplemento = new ArrayList<>();
        List<CFDI> ppdConComplemento = new ArrayList<>();
        List<CFDI> historicasVinculadas = new ArrayList<>();

        /**
         * Evaluación Contable de Facturas (Reglas SAT):
         * 1. Ingresos PUE: Acreditables si Uso CFDI es G01/G03 y están bancarizados (no 01, no 31).
         * 2. Ingresos PPD: El IVA se bloquea en el mes actual hasta que exista un documento CP01.
         * 3. Pagos (CP01): Acreditables solo si el mesPago coincide con el proceso, están bancarizados
         * y heredan Uso CFDI G01/G03 de su factura de ingreso relacionada.
         */
        for (CFDI c : listAllCFDI) {
            String tipo = c.getTipoDeComprobante();
            String metodoPago = c.getMetodoDePago();
            String formaPago = c.getFormaDePago();

            if ("I".equals(tipo)) {
                boolean usoValido = USOS_SAT.contains(c.getUsoCFDI());
                double subTot = setDecimal(c.getSubTotal());
                double iva = c.getTrasladoIVA() != null ? setDecimal(c.getTrasladoIVA()) : 0;
                double base = iva > 0 ? iva / 0.16 : 0;

                // Si no es G01 o G03, va a la bolsa de ignorados
                if (!usoValido) {
                    ignoradosUsoSubTotal += subTot;
                    ignoradosUsoBase += base;
                    ignoradosUsoIVA += iva;
                    continue; // El SAT no lo suma en esta sección
                }

                descuentos += c.getDescuento() != null ? setDecimal(c.getDescuento()) : 0;

                if ("PUE".equals(metodoPago)) {
                    if (FORMAS_PAGO_EXCLUIDAS.contains(formaPago)) {
                        excluidosSubTotal += subTot;
                        excluidosBase += base;
                        excluidosIVA += iva;
                    } else {
                        pueSubTotal += subTot;
                        pueBase += base;
                        pueIVA += iva;
                    }
                } else if ("PPD".equals(metodoPago) || "99".equals(formaPago)) {
                    boolean tieneComplemento = c.getUuidReferencia() != null && c.getUuidReferencia().startsWith("COMPLEMENTO:");
                    int mesFact = -1;

                    if (c.getFecha() != null) {
                        try {
                            mesFact = Integer.parseInt(c.getFecha().substring(5, 7));
                        } catch (Exception ignored) {}
                    }

                    if (mesFact == mesProceso) {
                        if (!tieneComplemento) {
                            ppdSinComplemento.add(c);
                            ppdSubTotal += setDecimal(c.getSubTotal());
                            double ivaO = c.getTrasladoIVAOriginal() != null ? setDecimal(c.getTrasladoIVAOriginal()) : 0;
                            ppdIVA += ivaO;
                            ppdBase += ivaO > 0 ? ivaO / 0.16 : 0;
                        } else {
                            ppdConComplemento.add(c);
                        }
                    } else if (mesFact != -1 && mesFact < mesProceso && tieneComplemento) {
                        historicasVinculadas.add(c);
                    }
                }
            } else if ("P".equals(tipo) && "CP01".equals(c.getUsoCFDI())) {
                int mesPago = c.getFechaPago() != null ? extraerMes(c.getFechaPago()) : -1;
                boolean formaPagoValida = formaPago != null && !FORMAS_PAGO_EXCLUIDAS.contains(formaPago);

                boolean usoParentValido = false;
                if (c.getUuidReferencia() != null) {
                    for (String parentUuid : c.getUuidReferencia().split(",")) {
                        String usoParent = UUID_TO_USO_CFDI.get(parentUuid.trim());
                        if (usoParent == null) {
                            LOGGER.log(Level.WARNING, "⚠ Complemento {0} sin XML origen procesado en memoria. UUID faltante: {1}",
                                    new Object[]{c.getNombreArchivo(), parentUuid});
                        } else if (USOS_SAT.contains(usoParent)) {
                            usoParentValido = true;
                            break;
                        }
                    }
                }

                if (mesPago == mesProceso && formaPagoValida && usoParentValido) {
                    cp01DelMes.add(c);
                    cp01SubTotal += setDecimal(c.getSubTotal());
                    double iva = c.getTrasladoIVA() != null ? setDecimal(c.getTrasladoIVA()) : 0;
                    cp01IVA += iva;
                    cp01Base += iva > 0 ? iva / 0.16 : 0;
                } else if (!usoParentValido && mesPago == mesProceso) {
                    // Complemento de una factura no acreditable
                    double subTot = setDecimal(c.getSubTotal());
                    double iva = c.getTrasladoIVA() != null ? setDecimal(c.getTrasladoIVA()) : 0;
                    double base = iva > 0 ? iva / 0.16 : 0;
                    ignoradosUsoSubTotal += subTot;
                    ignoradosUsoBase += base;
                    ignoradosUsoIVA += iva;
                }
            }
        }

        double totalBaseAcreditable = pueBase + cp01Base;
        double totalIVAAcreditable  = pueIVA  + cp01IVA;

        int fila = 0;

        Row rowTitulo = resumen.createRow(fila++);
        rowTitulo.setHeightInPoints(30);
        Cell cTitulo = rowTitulo.createCell(1);
        cTitulo.setCellValue("CONCENTRADO " + LABEL);
        cTitulo.setCellStyle(estTitulo);
        resumen.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 1, 5));

        fila++;

        Row rowH = resumen.createRow(fila++);
        crearCeldaEstilo(rowH, 1, "Concepto",  estHeader);
        crearCeldaEstilo(rowH, 3, "SubTotal",  estHeader);
        crearCeldaEstilo(rowH, 4, "Base",      estHeader);
        crearCeldaEstilo(rowH, 5, "IVA",       estHeader);

        Row rowPUE = resumen.createRow(fila++);
        crearCeldaEstilo(rowPUE, 1, "PUE — Facturas de Ingreso (G01/G03)", estConcepto);
        crearCeldaMoneda(rowPUE, 3, pueSubTotal, estMoneda);
        crearCeldaMoneda(rowPUE, 4, pueBase,     estMoneda);
        crearCeldaMoneda(rowPUE, 5, pueIVA,      estMoneda);

        Row rowCP01 = resumen.createRow(fila++);
        crearCeldaEstilo(rowCP01, 1, "CP01 — Complementos pagados en " + LABEL + " (G01/G03)", estConcepto);
        crearCeldaMoneda(rowCP01, 3, cp01SubTotal, estMoneda);
        crearCeldaMoneda(rowCP01, 4, cp01Base,     estMoneda);
        crearCeldaMoneda(rowCP01, 5, cp01IVA,      estMoneda);

        Row rowDesc = resumen.createRow(fila++);
        crearCeldaEstilo(rowDesc, 1, "Descuentos", estConcepto);
        crearCeldaMoneda(rowDesc, 3, -descuentos,  estMoneda);
        crearCeldaMoneda(rowDesc, 4, 0.0,          estMoneda);
        crearCeldaMoneda(rowDesc, 5, 0.0,          estMoneda);

        // Excluidos por Forma de Pago
        Row rowExcluidos = resumen.createRow(fila++);
        crearCeldaEstilo(rowExcluidos, 1, "Excluidos por Forma de Pago (01-Efectivo / 31)", estGrisLabel);
        crearCeldaMoneda(rowExcluidos, 3, -excluidosSubTotal, estGris);
        crearCeldaMoneda(rowExcluidos, 4, -excluidosBase,     estGris);
        crearCeldaMoneda(rowExcluidos, 5, -excluidosIVA,      estGris);

        // Ignorados por Uso CFDI
        Row rowIgnorados = resumen.createRow(fila++);
        crearCeldaEstilo(rowIgnorados, 1, "Ignorados por Uso CFDI Distinto a G01/G03", estGrisLabel);
        crearCeldaMoneda(rowIgnorados, 3, -ignoradosUsoSubTotal, estGris);
        crearCeldaMoneda(rowIgnorados, 4, -ignoradosUsoBase,     estGris);
        crearCeldaMoneda(rowIgnorados, 5, -ignoradosUsoIVA,      estGris);

        Row rowTotal = resumen.createRow(fila++);
        rowTotal.setHeightInPoints(20);
        crearCeldaEstilo(rowTotal, 1, "✔ TOTAL IVA ACREDITABLE DEL MES (SAT)", estTotalAcreditable);
        crearCeldaMoneda(rowTotal, 3, 0.0,                  estTotalAcreditable);
        crearCeldaMoneda(rowTotal, 4, totalBaseAcreditable, estTotalAcreditable);
        crearCeldaMoneda(rowTotal, 5, totalIVAAcreditable,  estTotalAcreditable);

        fila++;

        Row rowBloq = resumen.createRow(fila++);
        rowBloq.setHeightInPoints(20);
        crearCeldaEstilo(rowBloq, 1, "⚠ PPD BLOQUEADO — Pendiente de Complemento", estTotalBloqueado);
        crearCeldaMoneda(rowBloq, 3, ppdSubTotal, estTotalBloqueado);
        crearCeldaMoneda(rowBloq, 4, ppdBase,     estTotalBloqueado);
        crearCeldaMoneda(rowBloq, 5, ppdIVA,      estTotalBloqueado);

        fila += 2;

        // [Resto de construcción de tablas Detalle CP01 y Detalle PPD se mantienen igual]
        Row rowTituloCP01 = resumen.createRow(fila++);
        Cell cTituloCP01 = rowTituloCP01.createCell(1);
        cTituloCP01.setCellValue("Detalle CP01 — Complementos pagados en " + LABEL + " (Validados)");
        cTituloCP01.setCellStyle(estSubHeader);
        resumen.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(fila - 1, fila - 1, 1, 5));

        Row rowHCP01 = resumen.createRow(fila++);
        crearCeldaEstilo(rowHCP01, 1, "Emisor",   estHeader);
        crearCeldaEstilo(rowHCP01, 3, "SubTotal", estHeader);
        crearCeldaEstilo(rowHCP01, 4, "Base",     estHeader);
        crearCeldaEstilo(rowHCP01, 5, "IVA",      estHeader);

        for (CFDI c : cp01DelMes) {
            Row rowDet = resumen.createRow(fila++);
            double ivaC    = c.getTrasladoIVA() != null ? setDecimal(c.getTrasladoIVA()) : 0;
            double baseC   = ivaC > 0 ? ivaC / 0.16 : 0;
            double subTotC = setDecimal(c.getSubTotal());
            crearCeldaEstilo(rowDet, 1, c.getNombreEmisor(), estVerde);
            crearCeldaMoneda(rowDet, 3, subTotC, estVerde);
            crearCeldaMoneda(rowDet, 4, baseC,   estVerde);
            crearCeldaMoneda(rowDet, 5, ivaC,    estVerde);
        }

        fila += 2;

        Row rowTituloPPD = resumen.createRow(fila++);
        Cell cTituloPPD = rowTituloPPD.createCell(1);
        cTituloPPD.setCellValue("Detalle PPD (G01/G03) — " + ppdSinComplemento.size() + " pendientes / " + ppdConComplemento.size() + " con complemento");
        cTituloPPD.setCellStyle(estSubHeader);
        resumen.addMergedRegion(new CellRangeAddress(fila - 1, fila - 1, 1, 5));

        Row rowHPPD = resumen.createRow(fila++);
        crearCeldaEstilo(rowHPPD, 1, "Emisor",   estHeader);
        crearCeldaEstilo(rowHPPD, 3, "SubTotal", estHeader);
        crearCeldaEstilo(rowHPPD, 4, "Base",     estHeader);
        crearCeldaEstilo(rowHPPD, 5, "IVA",      estHeader);

        for (CFDI c : ppdSinComplemento) {
            Row rowDet = resumen.createRow(fila++);
            double ivaD  = c.getTrasladoIVAOriginal() != null ? setDecimal(c.getTrasladoIVAOriginal()) : 0;
            double baseD = ivaD > 0 ? ivaD / 0.16 : 0;
            String label = c.getNombreEmisor() + (ivaD == 0 ? " (Exento)" : " ⚠ IVA pendiente");
            crearCeldaEstilo(rowDet, 1, label, ivaD > 0 ? estNaranjaBold : estNaranja);
            crearCeldaMoneda(rowDet, 3, setDecimal(c.getSubTotal()), ivaD > 0 ? estNaranjaBold : estNaranja);
            crearCeldaMoneda(rowDet, 4, baseD, ivaD > 0 ? estNaranjaBold : estNaranja);
            crearCeldaMoneda(rowDet, 5, ivaD, ivaD > 0 ? estNaranjaBold : estNaranja);
        }

        fila++;

        for (CFDI c : historicasVinculadas) {
            Row rowDet = resumen.createRow(fila++);
            double ivaD  = c.getTrasladoIVAOriginal() != null ? setDecimal(c.getTrasladoIVAOriginal()) : 0;
            double baseD = ivaD > 0 ? ivaD / 0.16 : 0;

            String mesFactura = "";
            if (c.getFecha() != null && c.getFecha().length() >= 7) {
                String anio = c.getFecha().substring(0, 4);
                String mes  = c.getFecha().substring(5, 7);
                String[] meses = {"", "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
                try {
                    mesFactura = " [" + meses[Integer.parseInt(mes)] + " " + anio + "]";
                } catch (NumberFormatException e) {
                    mesFactura = " [" + mes + "/" + anio + "]";
                }
            }

            String refComplemento = c.getUuidReferencia().replace("COMPLEMENTO:", "");
            String label = c.getNombreEmisor() + mesFactura + " ✔ → " + refComplemento;

            crearCeldaEstilo(rowDet, 1, label, estVerde);
            crearCeldaMoneda(rowDet, 3, setDecimal(c.getSubTotal()), estVerde);
            crearCeldaMoneda(rowDet, 4, baseD, estVerde);
            crearCeldaMoneda(rowDet, 5, ivaD, estVerde);
        }
    }

    private static String determinarEstatusPago(CFDI cfdi) {
        if ("P".equals(cfdi.getTipoDeComprobante())) return "COMPLEMENTO";
        if ("PPD".equals(cfdi.getMetodoDePago()) || "99".equals(cfdi.getFormaDePago())) {
            if (cfdi.getUuidReferencia() != null && cfdi.getUuidReferencia().startsWith("COMPLEMENTO:")) {
                return "✔ PPD PAGADA";
            }
            return "⚠ PENDIENTE";
        }
        return "✔ PAGADA";
    }

    private static String determinarFechaPago(CFDI cfdi, String estatus) {
        if ("⚠ PENDIENTE".equals(estatus)) return "— PENDIENTE";

        String fecha = cfdi.getFechaPago() != null ? cfdi.getFechaPago() : cfdi.getFecha();

        if (fecha != null && fecha.length() >= 10) {
            try {
                LocalDate date = LocalDate.parse(fecha.substring(0, 10), FORMATTER_IN);
                String formatted = date.format(FORMATTER_OUT);
                // Retorna capitalizado: 09-abr-2026 -> 09-Abr-2026
                return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
            } catch (DateTimeParseException e) {
                LOGGER.log(Level.WARNING, "Error al formatear fecha: {0}", fecha);
                return fecha.substring(0, 10);
            }
        }
        return "—";
    }

    public static String determinarTipoCFDI(String tipoDeComprobante) {
        if (Objects.isNull(tipoDeComprobante)) return "Desconocido";
        switch (tipoDeComprobante) {
            case "I": return "Ingreso";
            case "E": return "Egreso";
            case "T": return "Traslado";
            case "N": return "Nómina";
            case "P": return "Pago";
            default:  return "Tipo de CFDI desconocido";
        }
    }

    // =========================================================================
    // CONSTRUCCIÓN DE FILAS
    // =========================================================================

    private static void createHeaderRow(Sheet pagina, Workbook workbook) {
        Row headerRow = pagina.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < COLUMNS_HEADERS.length; i++) {
            Cell celda = headerRow.createCell(i);
            celda.setCellValue(COLUMNS_HEADERS[i]);
            celda.setCellStyle(SPECIAL_COLUMNS.contains(COLUMNS_HEADERS[i]) ? createBoldItalicStyle(workbook) : headerStyle);
        }
    }

    private static void fillDataRows(Sheet pagina, List<CFDI> listCFDI, Workbook workbook) {
        int i = 0;
        for (CFDI cfdi : listCFDI) {
            String formaPagoClave = cfdi.getFormaDePago();
            FormaPago formaPagoEnum = FormaPago.fromClave(formaPagoClave);
            String formaPagoTexto = (formaPagoClave != null ? formaPagoClave : "N/A") + " - " +
                    (formaPagoEnum != null ? formaPagoEnum.getDescripcion() : "Descripción no encontrada");

            Row dataRow = pagina.createRow(i + 1);

            dataRow.createCell(0).setCellValue(cfdi.getNombreArchivo());
            dataRow.createCell(1).setCellValue(cfdi.getMetodoDePago());
            dataRow.createCell(2).setCellValue(cfdi.getUsoCFDI());
            dataRow.createCell(3).setCellValue(formaPagoTexto);
            dataRow.createCell(4).setCellValue(determinarTipoCFDI(cfdi.getTipoDeComprobante()));
            dataRow.createCell(5).setCellValue(cfdi.getTipoFactor() != null ? cfdi.getTipoFactor() : "N/A");
            dataRow.createCell(6).setCellValue(cfdi.getRfcEmisor());
            dataRow.createCell(7).setCellValue(cfdi.getNombreEmisor());

            setValueCell(dataRow, 8,  setDecimal(cfdi.getSubTotal()), createCurrencyItalicStyle(workbook));
            setValueCell(dataRow, 9,  setDecimal(Objects.isNull(cfdi.getTotalImpuestoTrasladados()) ? ZERO : cfdi.getTotalImpuestoTrasladados()), createCurrencyStyle(workbook));
            setValueCell(dataRow, 10, setDecimal(Objects.isNull(cfdi.getTotal())                    ? ZERO : cfdi.getTotal()),                    createCurrencyStyle(workbook));
            setValueCell(dataRow, 11, Objects.isNull(cfdi.getTrasladoIVA()) ? 0d : setDecimal(cfdi.getTrasladoIVA()),                             createCurrencyStyle(workbook));
            setValueCell(dataRow, 12, setDecimal(Objects.isNull(cfdi.getDescuento())  ? ZERO : cfdi.getDescuento()),  createCurrencyStyle(workbook));
            setValueCell(dataRow, 13, setDecimal(Objects.isNull(cfdi.getBaseIVA0())   ? ZERO : cfdi.getBaseIVA0()),   createCurrencyStyle(workbook));

            setFormulaCells(dataRow, i, workbook);

            String estatus = determinarEstatusPago(cfdi);
            Cell cellEstatus = dataRow.createCell(17);
            cellEstatus.setCellValue(estatus);
            cellEstatus.setCellStyle(createEstatusStyle(workbook, estatus));

            Cell cellFecha = dataRow.createCell(18);
            cellFecha.setCellValue(determinarFechaPago(cfdi, estatus));
            cellFecha.setCellStyle(createEstatusStyle(workbook, estatus));

            colorearFilaBloqueada(dataRow, cfdi, workbook);
            i++;
        }
    }

    private static void createTotalRow(Sheet pagina, int rowCount, Workbook workbook) {
        Row totalRow = pagina.createRow(rowCount + 1);
        setTotalFormulaCells(totalRow, rowCount, workbook);
    }

    private static void setTotalFormulaCells(Row totalRow, int rowCount, Workbook workbook) {
        setFormulaCell(totalRow, 8,  "SUM(I2:I"  + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 9,  "SUM(J2:J"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 10, "SUM(K2:K"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 11, "SUM(L2:L"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 12, "SUM(M2:M"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 13, "SUM(N2:N"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 14, "SUM(O2:O"  + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 15, "SUM(P2:P"  + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 16, "SUM(Q2:Q"  + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
    }

    // =========================================================================
    // HELPERS DE CELDAS Y ESTILOS
    // =========================================================================

    private static void crearCeldaEstilo(Row row, int col, String valor, CellStyle estilo) {
        Cell cell = row.createCell(col); cell.setCellValue(valor); cell.setCellStyle(estilo);
    }

    private static void crearCeldaMoneda(Row row, int col, Double valor, CellStyle estilo) {
        Cell cell = row.createCell(col); cell.setCellValue(valor); cell.setCellStyle(estilo);
    }

    private static void setValueCell(Row row, int cellIndex, Double value, CellStyle style) {
        Cell cell = row.createCell(cellIndex); cell.setCellValue(value); cell.setCellStyle(style);
    }

    private static void setFormulaCell(Row row, int cellIndex, String formula, CellStyle style) {
        Cell cell = row.createCell(cellIndex); cell.setCellFormula(formula); cell.setCellStyle(style);
    }

    private static void setFormulaCells(Row dataRow, int rowIndex, Workbook workbook) {
        int r = rowIndex + 2;
        setFormulaCell(dataRow, 14, "L" + r + "/0.16", createCurrencyItalicStyle(workbook));
        setFormulaCell(dataRow, 15, "O" + r + "*0.16", createCurrencyItalicStyle(workbook));
        setFormulaCell(dataRow, 16, "SUM(N" + r + ":P" + r + ")", createCurrencyStyle(workbook));
    }

    private static Double setDecimal(String value) {
        try {
            return (value == null || value.isBlank()) ? 0.0 : new BigDecimal(value.trim()).doubleValue();
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int extraerMes(String fecha) {
        if (fecha == null || fecha.length() < 7) return -1;
        try {
            return Integer.parseInt(fecha.substring(5, 7));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); style.setFont(font); return style;
    }

    private static CellStyle createBoldItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); font.setItalic(true); font.setColor(IndexedColors.LIGHT_BLUE.getIndex()); style.setFont(font); return style;
    }

    private static CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat((short) 8); return style;
    }

    private static CellStyle createCurrencyItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat((short) 8); Font font = workbook.createFont(); font.setItalic(true); font.setColor(IndexedColors.LIGHT_BLUE.getIndex()); style.setFont(font); return style;
    }

    private static CellStyle createCurrencyBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat((short) 8); Font font = workbook.createFont(); font.setBold(true); style.setFont(font); return style;
    }

    private static CellStyle createCurrencyBoldItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); style.setDataFormat((short) 8); Font font = workbook.createFont(); font.setBold(true); font.setItalic(true); font.setColor(IndexedColors.LIGHT_BLUE.getIndex()); style.setFont(font); return style;
    }

    private static void colorearFilaBloqueada(Row dataRow, CFDI cfdi, Workbook workbook) {
        boolean esPPDBloqueada = "I".equals(cfdi.getTipoDeComprobante())
                && ("PPD".equals(cfdi.getMetodoDePago()) || "99".equals(cfdi.getFormaDePago()))
                && (cfdi.getUuidReferencia() == null || !cfdi.getUuidReferencia().startsWith("COMPLEMENTO:"));

        if (!esPPDBloqueada) return;

        for (int col = 0; col < COLUMNS_HEADERS.length; col++) {
            Cell cell = dataRow.getCell(col);
            if (cell == null) cell = dataRow.createCell(col);

            CellStyle estiloNuevo = workbook.createCellStyle();
            CellStyle estiloActual = cell.getCellStyle();
            if (estiloActual != null) estiloNuevo.cloneStyleFrom(estiloActual);

            estiloNuevo.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            estiloNuevo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cell.setCellStyle(estiloNuevo);
        }
    }

    private static CellStyle createEstatusStyle(Workbook workbook, String estatus) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        switch (estatus) {
            case "✔ PAGADA":
            case "COMPLEMENTO": font.setColor(IndexedColors.GREEN.getIndex()); font.setBold(true); break;
            case "⚠ PENDIENTE": font.setColor(IndexedColors.DARK_RED.getIndex()); font.setBold(true); break;
            default: font.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); font.setItalic(true);
        }
        style.setFont(font); return style;
    }

    private static void guardarArchivoXLSX(Workbook workbook) {
        String rutaSalida = java.nio.file.Paths.get(DIRECTORY, LABEL + ".xlsx").toString();
        try (FileOutputStream salida = new FileOutputStream(rutaSalida)) {
            workbook.write(salida);
            LOGGER.log(Level.INFO, "Archivo creado exitosamente en {0}", rutaSalida);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error al guardar XLSX", ex);
        }
    }
}