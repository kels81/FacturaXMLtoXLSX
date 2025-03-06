package com.mx.main;

import com.mx.bean.CFDI;
import com.mx.utils.Constantes;
import com.mx.utils.XmlNode;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.mx.utils.MetodoPago.PPD;
import static com.mx.utils.MetodoPago.PUE;
import static com.mx.utils.UsoCFDI.G03;

public class FacturasXMLtoXLSX {

    private static final Set<String> SPECIAL_COLUMNS = Set.of("BASE", "SUB TOTAL", "IVA");
    private static final String ZERO = "0";
    private static final Logger LOGGER = Logger.getLogger("newexcel.ExcelOOXML");
    private static final Map<String, String> TRASLADADOS = new HashMap<>();
    private static String LABEL;
    private static String DIRECTORY;
    private static final String[] COLUMNS_HEADERS = {"XML", "Metodo\nPago", "Uso\nCFDI", "Tipo\nComprobante", "RFC\nEmisor", "Nombre\nEmisor", "SUB TOTAL", "Total\nImpuesto Trasladado", "Total", "Traslado\nIVA: 16", "BASE", "IVA", "TOTAL"};

    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();

    public static void main(String[] args) {
        int noMes = 2;
        DIRECTORY = Constantes.getDirectoryForMonth(noMes);
        LABEL = Constantes.getMonthName(noMes).toUpperCase();

        List<CFDI> filesCFDI = cfdiFile(new File(DIRECTORY));

        /*int count = 1;
        for (CFDI cfdi : filesCFDI) {
            System.out.println("***********************************************************************");
            System.out.println("### = " + count);
            System.out.println("Nombre: " + cfdi.getNombreArchivo());
            System.out.println("Fecha: " + cfdi.getFecha());
            System.out.println("Lugar Expedicion: " + cfdi.getLugarExpedicion());
            System.out.println("RFC Emisor: " + cfdi.getRfcEmisor());
            System.out.println("Nombre Emisor: " + cfdi.getNombreEmisor());
            System.out.println("RFC Receptor: " + cfdi.getRfcReceptor());
            System.out.println("Nombre Receptor: " + cfdi.getNombreReceptor());
            System.out.println("Moneda: " + cfdi.getMoneda());
            System.out.println("Forma Pago: " + cfdi.getFormaDePago());
            System.out.println("Metodo Pago: " + cfdi.getMetodoDePago());
            System.out.println("Subtotal: " + cfdi.getSubTotal());
            System.out.println("Total: " + cfdi.getTotal());
            System.out.println("Total Imp Trasladados: " + cfdi.getTotalImpuestoTrasladados());
            System.out.println("Traslado IVA: " + cfdi.getTrasladoIVA());
            System.out.println("Tasa IVA: " + cfdi.getTasaIVA());
            System.out.println("Traslado IEPS: " + cfdi.getTrasladoIEPS());
            System.out.println("Tasa IEPS: " + cfdi.getTasaIEPS());
            count++;
        }*/

        /*createXLSX(filesCFDI
                .stream()
                //.filter(cfdi -> !cfdi.getTrasladoIVA().equals("0"))
                .filter(cfdi -> Objects.nonNull(cfdi.getTrasladoIVA()) && Double.parseDouble(cfdi.getTrasladoIVA()) > 0)
                .collect(Collectors.toList()));*/

        // Ordenar la lista por RFC del emisor
        filesCFDI.sort(Comparator.comparing(CFDI::getRfcEmisor));

        createXLSX(filesCFDI);
    }

    private static List<CFDI> cfdiFile(File directory) {
        return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
                .filter(file -> {
                    String extension = FilenameUtils.getExtension(file.getPath());
                    return !extension.equalsIgnoreCase("zip") && !extension.equalsIgnoreCase("xlsx");
                })
                .map(FacturasXMLtoXLSX::processFile)
                .collect(Collectors.toList());
    }

    private static  List<CFDI> lstCFDIFilter(List<CFDI> filesCFDI, String filtroMetodoPago, String filtroUsoCFDI) {
        return filesCFDI.stream()
                .filter(cfdi -> {
                    boolean metodoPagoValido = StringUtils.isEmpty(filtroMetodoPago) ||
                            Objects.equals(cfdi.getMetodoDePago(), filtroMetodoPago);

                    boolean usoCFDIValido = StringUtils.isEmpty(filtroUsoCFDI) ||
                            Objects.equals(cfdi.getUsoCFDI(), filtroUsoCFDI);

                    return metodoPagoValido && usoCFDIValido;
                })
                .collect(Collectors.toList());
    }

    private static CFDI processFile(File xmlFile) {
        CFDI cfdi = new CFDI();

        Document xmlDocument = getDocument(xmlFile);
        xmlDocument.getDocumentElement().normalize();

        //Imprimir y conocer todos los nodos del xml
        //getNodesXML(xmlDocument);

        // Asignamos el nombre del archivo
        cfdi.setNombreArchivo(xmlFile.getName());

        // Llamamos al método que recorre el XML usando XmlNode
        exploreNodes(xmlDocument.getDocumentElement(), cfdi);


        return cfdi;
    }

    private static Document getDocument(File xmlFile) {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            return builder.parse(xmlFile);
        } catch (ParserConfigurationException | IOException | org.xml.sax.SAXException e) {
            LOGGER.log(Level.SEVERE, "Error al procesar el archivo XML", e);
            return null;
        }
    }

    //##########################################

    private static void getNodesXML(Document xmlDoc) {
        xmlDoc.getDocumentElement().normalize();
        Element root = xmlDoc.getDocumentElement();
        System.out.println("🌳 Nodo raíz: " + root.getNodeName());
        // 🔹 Verificar y mostrar atributos del nodo raíz
        printAttributes22(root, 1);

        // 4️⃣ Llamar a la función para recorrer hijos
        exploreNodes22(root, 1);

    }

    // Método para recorrer todos los nodos
    private static void exploreNodes22(Node node, int depth) {
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);

            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) currentNode;

                // Obtener el enum correspondiente
                XmlNode xmlNode = XmlNode.fromNodeName(element.getNodeName());
                if (xmlNode != null) {
                    System.out.println(" ".repeat(depth * 2) + "📌 Nodo: " + xmlNode.getNodeName());
                } else {
                    System.out.println(" ".repeat(depth * 2) + "📌 Nodo desconocido: " + element.getNodeName());
                }

                // Mostrar atributos del nodo
                printAttributes22(element, depth + 1);

                // Mostrar contenido de texto si lo tiene
                if (!element.getTextContent().trim().isEmpty()) {
                    System.out.println(" ".repeat((depth + 1) * 2) + "📄 Contenido: " + element.getTextContent().trim());
                }

                // Recursión para recorrer hijos
                exploreNodes22(element, depth + 1);
            }
        }
    }

    // Método para imprimir atributos de un nodo
    private static void printAttributes22(Element element, int depth) {
        NamedNodeMap attributes = element.getAttributes();
        for (int j = 0; j < attributes.getLength(); j++) {
            Node attr = attributes.item(j);
            System.out.println(" ".repeat(depth * 2) + "🔹 Atributo: " + attr.getNodeName() + " = " + attr.getNodeValue());
        }
    }
    //##########################################


    // Método que recorre el XML y extrae los datos dinámicamente usando XmlNode
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
            }
        }

        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            exploreNodes(nodeList.item(i), cfdi);
        }
    }

    // Método para mapear valores automáticamente en la clase CFDI
    private static void mapToCFDI(CFDI cfdi, XmlNode node, String attr, String value) {
        switch (node) {
            case CFDI_COMPROBANTE:
                switch (attr) {
                    case "Fecha": cfdi.setFecha(value); break;
                    case "LugarExpedicion": cfdi.setLugarExpedicion(value); break;
                    case "Moneda": cfdi.setMoneda(value); break;
                    case "FormaPago": cfdi.setFormaDePago(value); break;
                    case "MetodoPago": cfdi.setMetodoDePago(value); break;
                    case "TipoDeComprobante": cfdi.setTipoDeComprobante(value); break;
                    case "SubTotal": cfdi.setSubTotal(value); break;
                    case "Total": cfdi.setTotal(value); break;
                }
                break;

            case CFDI_EMISOR:
                switch (attr) {
                    case "Rfc": cfdi.setRfcEmisor(value); break;
                    case "Nombre": cfdi.setNombreEmisor(value); break;
                }
                break;

            case CFDI_RECEPTOR:
                switch (attr) {
                    case "Rfc": cfdi.setRfcReceptor(value); break;
                    case "Nombre": cfdi.setNombreReceptor(value); break;
                    case "UsoCFDI": cfdi.setUsoCFDI(value); break;
                }
                break;

            case CFDI_TRASLADO:
                // Guardamos los valores en el Map para procesarlos después
                TRASLADADOS.put(attr, value);

                // Si ya tenemos la información suficiente, procesamos los traslados
                if (TRASLADADOS.containsKey("Impuesto") || TRASLADADOS.containsKey("impuesto")) {
                    workWithTrasladados(cfdi);
                    TRASLADADOS.clear(); // Limpiamos el mapa para el siguiente traslado
                }
                break;

            case CFDI_IMPUESTOS:
                if ("TotalImpuestosTrasladados".equals(attr)) {
                    cfdi.setTotalImpuestoTrasladados(value);
                }
                break;
        }
    }

    public static String determinarTipoCFDI(String  tipoDeComprobante) {
        if (Objects.isNull(tipoDeComprobante)) {
            return "Desconocido"; // Manejo de casos nulos
        }

        switch (tipoDeComprobante) {
            case "I":
                return "Ingreso";   //(Factura de venta)
            case "E":
                return "Egreso";    //(Nota de crédito, devolución)
            case "T":
                return "Traslado";  //(Transporte de mercancías)
            case "N":
                return "Nómina";    //(Pago de sueldos)
            case "P":
                return "Pago";      //(Complemento de pago)
            default:
                return "Tipo de CFDI desconocido";
        }
    }

    private static void workWithTrasladados(CFDI cfdi) {
        String impuesto = Optional.ofNullable(TRASLADADOS.get("impuesto")).orElse(TRASLADADOS.get("Impuesto"));
        String tasa = Optional.ofNullable(TRASLADADOS.get("tasa")).orElse(TRASLADADOS.get("TasaOCuota"));
        String importe = Optional.ofNullable(TRASLADADOS.get("importe")).orElse(TRASLADADOS.get("Importe"));

        if (importe == null) {
            importe = ZERO; // Asignamos un valor predeterminado
        }

        if (impuesto != null && (impuesto.equals("IVA") || impuesto.equals("002"))) {
            cfdi.setTrasladoIVA(importe.isEmpty() ? ZERO : importe);
            cfdi.setTasaIVA(tasa);
        } else {
            cfdi.setTrasladoIEPS(importe.isEmpty() ? ZERO : importe);
            cfdi.setTasaIEPS(tasa);
        }

        TRASLADADOS.clear();
    }

    private static void createXLSX(List<CFDI> listAllCFDI) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet pagina1 = workbook.createSheet(LABEL);
            Sheet pagina2 = workbook.createSheet(PUE.getClave());    //FACTURAS RECIBIDAS TIPO INGRESO
            Sheet pagina3 = workbook.createSheet(PPD.getClave());    //FACTURAS RECIBIDAS TIPO PAGO

            createHeaderRow(pagina1, workbook);
            fillDataRows(pagina1, listAllCFDI, workbook);
            createTotalRow(pagina1, listAllCFDI.size(), workbook);

            List<CFDI> lstCFDITipoIngreso = lstCFDIFilter(listAllCFDI, PUE.getClave(), G03.getClave());
            createHeaderRow(pagina2, workbook);
            fillDataRows(pagina2, lstCFDITipoIngreso, workbook);
            createTotalRow(pagina2, lstCFDITipoIngreso.size(), workbook);

            List<CFDI> lstCFDITipoPago = lstCFDIFilter(listAllCFDI, PPD.getClave(), G03.getClave());
            createHeaderRow(pagina3, workbook);
            fillDataRows(pagina3, lstCFDITipoPago, workbook);
            createTotalRow(pagina3, lstCFDITipoPago.size(), workbook);

            guardarArchivoXLSX(workbook);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al crear el archivo XLSX", e);
        }
    }

    private static void createHeaderRow(Sheet pagina, Workbook workbook) {
        Row headerRow = pagina.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < COLUMNS_HEADERS.length; i++) {
            Cell celda = headerRow.createCell(i);
            celda.setCellStyle(headerStyle);
            celda.setCellValue(COLUMNS_HEADERS[i]);
            // Aplicar estilo especial para columnas específicas
            if (SPECIAL_COLUMNS.contains(COLUMNS_HEADERS[i])) {
                celda.setCellStyle(createBoldItalicStyle(workbook));
            }
        }
    }

    private static void fillDataRows(Sheet pagina, List<CFDI> listCFDI, Workbook workbook) {
        int i = 0;
        for (CFDI cfdi : listCFDI) {
            Row dataRow = pagina.createRow(i + 1);
            dataRow.createCell(0).setCellValue(cfdi.getNombreArchivo());
            dataRow.createCell(1).setCellValue(cfdi.getMetodoDePago());
            dataRow.createCell(2).setCellValue(cfdi.getUsoCFDI());
            dataRow.createCell(3).setCellValue(determinarTipoCFDI(cfdi.getTipoDeComprobante()));
            dataRow.createCell(4).setCellValue(cfdi.getRfcEmisor());
            dataRow.createCell(5).setCellValue(cfdi.getNombreEmisor());
            setValueCell(dataRow, 6, setDecimal(cfdi.getSubTotal()), createCurrencyItalicStyle(workbook));
            setValueCell(dataRow, 7, setDecimal(Objects.isNull(cfdi.getTotalImpuestoTrasladados()) ? ZERO : cfdi.getTotalImpuestoTrasladados()), createCurrencyStyle(workbook));
            setValueCell(dataRow, 8, setDecimal(Objects.isNull(cfdi.getTotal()) ? ZERO : cfdi.getTotal()), createCurrencyStyle(workbook));
            setValueCell(dataRow, 9, Objects.isNull(cfdi.getTrasladoIVA()) ? 0 : setDecimal(cfdi.getTrasladoIVA()), createCurrencyStyle(workbook));
            //setValueCell(dataRow, 10, Objects.isNull(cfdi.getTrasladoIEPS()) ? 0 : setDecimal(cfdi.getTrasladoIEPS()), createCurrencyStyle(workbook));
            setFormulaCells(dataRow, i, workbook);
            i++;
        }
    }

    private static void setValueCell(Row row, int cellIndex, Double value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style); // Aplicar formato de moneda

    }

    private static void setFormulaCell(Row row, int cellIndex, String formula, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private static void setFormulaCells(Row dataRow, int rowIndex, Workbook workbook) {
        setFormulaCell(dataRow, 10, "J" + (rowIndex + 2) + "/0.16", createCurrencyItalicStyle(workbook));
        setFormulaCell(dataRow, 11, "K" + (rowIndex + 2) + "*0.16", createCurrencyItalicStyle(workbook));
        setFormulaCell(dataRow, 12, "SUM(K" + (rowIndex + 2) + ":L" + (rowIndex + 2) + ")", createCurrencyStyle(workbook));
    }

    private static void createTotalRow(Sheet pagina, int rowCount, Workbook workbook) {
        Row totalRow = pagina.createRow(rowCount + 1);
        setTotalFormulaCells(totalRow, rowCount, workbook);
    }

    private static void setTotalFormulaCells(Row totalRow, int rowCount, Workbook workbook) {
        setFormulaCell(totalRow, 6, "SUM(G2:G" + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 7, "SUM(H2:H" + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 8, "SUM(I2:I" + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 9, "SUM(J2:J" + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
        setFormulaCell(totalRow, 10, "SUM(K2:K" + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 11, "SUM(L2:L" + (rowCount + 1) + ")", createCurrencyBoldItalicStyle(workbook));
        setFormulaCell(totalRow, 12, "SUM(M2:M" + (rowCount + 1) + ")", createCurrencyBoldStyle(workbook));
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font fontBold = workbook.createFont();
        fontBold.setBold(true);
        headerStyle.setFont(fontBold);
        return headerStyle;
    }

    private static CellStyle createBoldItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setItalic(true);
        font.setColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle createCurrencyBoldItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat((short) 8);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setItalic(true);
        font.setColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle createCurrencyItalicStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat((short) 8);
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private static CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat((short) 8);
        return style;
    }

    private static CellStyle createCurrencyBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat((short) 8);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static Double setDecimal(String value) {
        return new BigDecimal(value).doubleValue();
    }

    private static void guardarArchivoXLSX(Workbook workbook) {
        try (FileOutputStream salida = new FileOutputStream(DIRECTORY + "\\" + LABEL + ".xlsx")) {
            workbook.write(salida);
            LOGGER.log(Level.INFO, "Archivo creado exitosamente en {0}", DIRECTORY + "\\" + LABEL + ".xlsx");
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Archivo no localizable en sistema de archivos", ex);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error de entrada/salida", ex);
        }
    }

}