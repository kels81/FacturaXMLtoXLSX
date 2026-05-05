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