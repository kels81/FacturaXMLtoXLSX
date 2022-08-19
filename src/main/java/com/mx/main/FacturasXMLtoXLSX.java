/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mx.main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mx.bean.CFDI;
import com.mx.utils.Constantes;
import com.mx.utils.TagsCFDI_32;
import com.mx.utils.TagsCFDI_33;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.util.HSSFColor;

/**
 *
 * @author Edrd
 */
public class FacturasXMLtoXLSX {

    private static File xmlFile;
    private static final Map<String, String> TRASLADADOS = new HashMap<>();
    private static final String[] COLUMNS_HEADERS = {"XML", "RFC Emisor", "Nombre Emisor", "Sub Total", "Total impuesto Trasladado", "Total", "Traslado IVA: 16", "Traslado IEPS: 8", "BASE", "IVA", "TOTAL"};
    private static String label;
    private static final Logger LOGGER = Logger.getLogger("newexcel.ExcelOOXML");
    private static final String COMPROBANTE = "cfdi:Comprobante";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String[] array = Constantes.DIRECTORY.split("\\\\");
        //System.out.println("array1 = " + array.length);
        //System.out.println("array = " + Arrays.toString(array));
        //int index = Arrays.asList(array).indexOf(Constantes.ROOT_DIRECTORY);        //CAMBIAR VALOR EN CONSTANTES PAR NILA
        //String[] newArray = Arrays.copyOfRange(array, index, array.length);
        //System.out.println("newArray1 = " + newArray.length);
        //System.out.println("newArray = " + Arrays.toString(newArray));
        //System.out.println("index = " + index);
        //System.out.println("Parent = " + new File(Constantes.DIRECTORY).getParent());

        label = array[array.length - 1].substring(3).toUpperCase();
        List<CFDI> filesCFDI = cfdiFile(new File(Constantes.DIRECTORY));

        System.out.println("filesCFDI = " + filesCFDI.size());

//        System.out.println("***********************************************************************");
//        System.out.println("Nombre: " + cfdi.getNombreArchivo());
//        System.out.println("Fecha: " + cfdi.getFecha());
//        System.out.println("Lugar Expedicion: " + cfdi.getLugarExpedicion());
//        System.out.println("RFC Emisor: " + cfdi.getRfcEmisor());
//        System.out.println("Nombre Emisor: " + cfdi.getNombreEmisor());
//        System.out.println("RFC Receptor: " + cfdi.getRfcReceptor());
//        System.out.println("Nombre Receptor: " + cfdi.getNombreReceptor());
//        System.out.println("Modena: " + cfdi.getMoneda());
//        System.out.println("Forma Pago: " + cfdi.getFormaDePago());
//        System.out.println("Metodo Pago: " + cfdi.getMetodoDePago());
//        System.out.println("Subtotal: " + cfdi.getSubTotal());
//        System.out.println("Total: " + cfdi.getTotal());
//        System.out.println("Total Imp Trasladados: " + cfdi.getTotalImpuestoTrasladados());
//        System.out.println("Traslado IVA: " + cfdi.getTrasladoIVA());
//        System.out.println("Tasa IVA: " + cfdi.getTasaIVA());
//        System.out.println("Traslado IEPS: " + cfdi.getTrasladoIEPS());
//        System.out.println("Tasa IEPS: " + cfdi.getTasaIEPS());
        
        createXLSX(filesCFDI
                .stream()
//                .filter(cfdi -> !cfdi.getTrasladoIVA().equals("0"))
                .filter(cfdi -> Objects.nonNull(cfdi.getTrasladoIVA()) && Double.parseDouble(cfdi.getTrasladoIVA()) > 0)
                .collect(Collectors.toList()));

    }

    private static List<CFDI> cfdiFile(File directory) {
        List<CFDI> filesCFDI = new ArrayList<>();

        File[] filesXML = directory.listFiles();
        for (File file : filesXML) {
            String extension = FilenameUtils.getExtension(file.getPath()).toLowerCase();
            if (!extension.equals("zip")) {
                CFDI cfdi = new CFDI();
                Document xmlDoc = getDocument(file.getPath());
                //xmlDoc.getDocumentElement().normalize();

//                List<String[]> allCFDITags = new ArrayList<>();
//                for (TagsCFDI_32 tagCDFI : TagsCFDI_32.values()) {
//                    allCFDITags.add(tagCDFI.getArrayTagsCFDI());
//                }
//
//                for (String[] array : allCFDITags) {
//                    String tagName = "cfdi:" + capitalize(TagsCFDI_32.values()[allCFDITags.indexOf(array)].toString().toLowerCase());
//
//                    NodeList nList = xmlDoc.getElementsByTagName(tagName);
//                    getElementAndAttributes(nList, array, cfdi);
//                }
                
                String versionCFDI = getVersionCFDI(xmlDoc);
                
                if (versionCFDI.equals("3.2")) {
                    getCFDITags32(xmlDoc, cfdi);
                }else {
                    getCFDITags33(xmlDoc, cfdi);
                }
                
                filesCFDI.add(cfdi);
            }

        }

        return filesCFDI;
    }

    private static Document getDocument(String docString) {
        try {
            xmlFile = new File(docString);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(xmlFile);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
    
    private static String getVersionCFDI(Document xmlDoc) {
        NodeList nList = xmlDoc.getElementsByTagName(COMPROBANTE);
        String version = "";
           try {
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);

                //System.out.println("\nCurrent Element: " + nNode.getNodeName());
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element elementName = (Element) nNode;
                    version = (elementName.getAttribute("version") == null ? elementName.getAttribute("Version") : elementName.getAttribute("version"));
                }
            }

        } catch (Exception e) {
        }
           return version;
    }

    private static void getCFDITags32(Document xmlDoc, CFDI cfdi) {
        List<String[]> allCFDITags = new ArrayList<>();
        for (TagsCFDI_32 tagCDFI : TagsCFDI_32.values()) {
            allCFDITags.add(tagCDFI.getArrayTagsCFDI());
        }

        for (String[] array : allCFDITags) {
            String tagName = "cfdi:" + capitalize(TagsCFDI_32.values()[allCFDITags.indexOf(array)].toString().toLowerCase());

            NodeList nList = xmlDoc.getElementsByTagName(tagName);
            getElementAndAttributes(nList, array, cfdi);
        }
    }

    private static void getCFDITags33(Document xmlDoc, CFDI cfdi) {
        List<String[]> allCFDITags = new ArrayList<>();
        for (TagsCFDI_33 tagCDFI : TagsCFDI_33.values()) {
            allCFDITags.add(tagCDFI.getArrayTagsCFDI());
        }

        for (String[] array : allCFDITags) {
            String tagName = "cfdi:" + capitalize(TagsCFDI_33.values()[allCFDITags.indexOf(array)].toString().toLowerCase());

            NodeList nList = xmlDoc.getElementsByTagName(tagName);
            getElementAndAttributes(nList, array, cfdi);
        }
    }

    private static void getElementAndAttributes(NodeList nList, String[] tags, CFDI cfdi) {
        try {
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);

                //System.out.println("\nCurrent Element: " + nNode.getNodeName());
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element elementName = (Element) nNode;
                    for (String tag : tags) {
                        //System.out.format("    " + tag + ": %s \n", elementName.getAttribute(tag));
                        createCFDI(nNode.getNodeName(), tag, elementName.getAttribute(tag), cfdi);                        
                    }
                }
            }

        } catch (Exception e) {
        }
    }

    private static String capitalize(final String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    private static void createCFDI(String nodeName, String tag, String value, CFDI cfdi) {
        cfdi.setNombreArchivo(xmlFile.getName());

        String[] parts = nodeName.split(":");
        String node = parts[1];
        //COMPROBANTE
        if (tag.equals("fecha") || tag.equals("Fecha")) {
            cfdi.setFecha(value);
        } else if (tag.equals("LugarExpedicion")) {
            cfdi.setLugarExpedicion(value);
        } else if ((tag.equals("rfc") || tag.equals("Rfc")) && node.equals("Emisor")) {
            cfdi.setRfcEmisor(value);
        } else if ((tag.equals("nombre") || tag.equals("Nombre")) && node.equals("Emisor")) {
            cfdi.setNombreEmisor(value);
        } else if ((tag.equals("rfc") || tag.equals("Rfc")) && node.equals("Receptor")) {
            cfdi.setRfcReceptor(value);
        } else if ((tag.equals("nombre") || tag.equals("Nombre")) && node.equals("Receptor")) {
            cfdi.setNombreReceptor(value);
        } else if (tag.equals("Moneda")) {
            cfdi.setMoneda(value);
        } else if (tag.equals("formaDePago") || tag.equals("FormaPago")) {
            cfdi.setFormaDePago(value);
        } else if (tag.equals("metodoDePago") || tag.equals("MetodoPago")) {
            cfdi.setMetodoDePago(value);
        } else if (tag.equals("subTotal") || tag.equals("SubTotal")) {
            cfdi.setSubTotal(value.equals("") ? "0" : value);
        } else if (tag.equals("total") || tag.equals("Total")) {
            cfdi.setTotal(value.equals("") ? "0" : value);
        } else if (tag.equals("totalImpuestosTrasladados") || tag.equals("TotalImpuestosTrasladados")) {
            cfdi.setTotalImpuestoTrasladados(value.equals("") ? "0" : value);
        } else if (node.equals("Traslado")) {
            TRASLADADOS.put(tag, value);
            if (tag.equals("importe") || tag.equals("Importe")) {
                workWithTrasladados(cfdi);
            }
        }

    }

    //private static void workWithTrasladados(Map<String, String> mapTrasladados, CFDI cfdi) {
    private static void workWithTrasladados(CFDI cfdi) {
        String impuesto = (TRASLADADOS.get("impuesto") == null ? TRASLADADOS.get("Impuesto") : TRASLADADOS.get("impuesto"));
        String tasa = (TRASLADADOS.get("tasa") == null ? TRASLADADOS.get("TasaOCuota") : TRASLADADOS.get("tasa"));
        String importe = (TRASLADADOS.get("importe") == null ? TRASLADADOS.get("Importe") : TRASLADADOS.get("importe"));
        if (impuesto.equals("IVA") || impuesto.equals("002")) {
            cfdi.setTrasladoIVA(importe.equals("") ? "0" : importe);
            cfdi.setTasaIVA(tasa);
            //createFormulasImpuestos(cfdi);
        } else {
            cfdi.setTrasladoIEPS(importe.equals("") ? "0" : importe);
            cfdi.setTasaIEPS(tasa);
        }
        TRASLADADOS.clear();
    }
    
    private static void createFormulasImpuestos(CFDI cfdi) {
        Double base = Double.parseDouble(cfdi.getTrasladoIVA()) / 0.16;
        Double iva = base * 0.16;
        Double total = base + iva;
        
        cfdi.setBase(base);
        cfdi.setIva(iva);
        cfdi.setTotalImp(total);
    }

    private static String objectToJson(Object object) {
        //Gson gson = new Gson();
        //Convert object to JSON string and pretty print
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        //Convert object to JSON string
        String json = gson.toJson(object);
        System.out.println("json = " + json);

        return json;
    }

    private static void createXLSX(List<CFDI> listCFDI) {

        // Creamos el libro de trabajo de Excel formato OOXML
        Workbook workbook = new XSSFWorkbook();

        // La hoja donde pondremos los datos
        Sheet pagina = workbook.createSheet(label);

        // Creamos el estilo para las celdas del encabezado
        CellStyle headerStyle = workbook.createCellStyle();
        Font fontBold = workbook.createFont();
        fontBold.setBold(true);
        headerStyle.setFont(fontBold);
        
        // Creamos el estilo para las celdas de tipo currency y cursiva
        CellStyle currencyCursiveStyle = workbook.createCellStyle();
        currencyCursiveStyle.setDataFormat((short)8);
        Font fontItalic = workbook.createFont();
        fontItalic.setItalic(true);
        fontItalic.setColor(HSSFColor.HSSFColorPredefined.LIGHT_BLUE.getIndex());
        currencyCursiveStyle.setFont(fontItalic);
        
        // Creamos el estilo para las celdas de tipo currency y cursiva
        CellStyle boldCursiveStyle = workbook.createCellStyle();
        Font fontBoldCursive = workbook.createFont();
        fontBoldCursive.setBold(true);
        fontBoldCursive.setItalic(true);
        fontBoldCursive.setColor(HSSFColor.HSSFColorPredefined.LIGHT_BLUE.getIndex());
        boldCursiveStyle.setFont(fontBoldCursive);
        
        // Creamos el estilo para las celdas de tipo currency y negrita
        CellStyle currencyBoldStyle = workbook.createCellStyle();
        currencyBoldStyle.setDataFormat((short)8);
        currencyBoldStyle.setFont(fontBold);
        
        // Creamos el estilo para las celdas de tipo currency
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat((short)8);

        // Creamos una fila en la hoja en la posicion 0
        Row headerRow = pagina.createRow(0);

        // Creamos el encabezado
        for (int i = 0; i < COLUMNS_HEADERS.length; i++) {
            // Creamos una celda en esa fila, en la posicion 
            // indicada por el contador del ciclo
            Cell celda = headerRow.createCell(i);

            // Indicamos el estilo que deseamos 
            // usar en la celda, en este caso el unico 
            // que hemos creado
            celda.setCellStyle(headerStyle);
            celda.setCellValue(COLUMNS_HEADERS[i]);
            if (COLUMNS_HEADERS[i].equals("BASE")) {
                celda.setCellStyle(boldCursiveStyle);
            }
        }

        int i = 0;
        for (CFDI cfdi : listCFDI) {
            Row dataRow = pagina.createRow(i + 1);
            
//            //Se convierte el jason a map para recorrerlo
//            Gson gson = new Gson();
//            Map<String, String> map = setCellValues(gson.fromJson(objectToJson(cfdi), new TypeToken<Map<String, String>>() {}.getType()));
//            // Y colocamos los datos en esa fila
//            int count = 0;
//            for (Map.Entry<String, String> entry : map.entrySet()) {
//                Cell celda = dataRow.createCell(count);
//                celda.setCellValue(entry.getValue());
//                count++;
//            }
            dataRow.createCell(0).setCellValue(cfdi.getNombreArchivo());
            //System.out.println("nombre = " + cfdi.getNombreArchivo());
            dataRow.createCell(1).setCellValue(cfdi.getRfcEmisor());
            dataRow.createCell(2).setCellValue(cfdi.getNombreEmisor());
            dataRow.createCell(3).setCellValue(setDecimal(cfdi.getSubTotal()));
            //System.out.println("val1 = " + cfdi.getSubTotal());
            dataRow.createCell(4).setCellValue(setDecimal(cfdi.getTotalImpuestoTrasladados() == null ? "0" : cfdi.getTotalImpuestoTrasladados()));
            //System.out.println("val2 = " + cfdi.getTotalImpuestoTrasladados());
            dataRow.createCell(5).setCellValue(setDecimal(cfdi.getTotal() == null ? "0" : cfdi.getTotal()));
            //System.out.println("val3 = " + cfdi.getTotal());
            dataRow.createCell(6).setCellValue(cfdi.getTrasladoIVA() == null ? 0 : setDecimal(cfdi.getTrasladoIVA()));
            dataRow.createCell(7).setCellValue(cfdi.getTrasladoIEPS() == null ? 0 : setDecimal(cfdi.getTrasladoIEPS()));
            
            Cell cellBase =dataRow.createCell(8);
            //cellBase.setCellValue(cfdi.getBase() == null ? 0 : cfdi.getBase());
            cellBase.setCellStyle(currencyCursiveStyle);
            cellBase.setCellFormula("G"+(i+2)+"/0.16");
            Cell cellIva = dataRow.createCell(9);
            //cellIva.setCellValue(cfdi.getIva() == null ? 0 : cfdi.getIva());
            cellIva.setCellStyle(currencyStyle);
            cellIva.setCellFormula("I"+(i+2)+"*0.16");
            Cell cellTotal = dataRow.createCell(10);
            //cellTotal.setCellValue(cfdi.getTotalImp() == null ? 0 : cfdi.getTotalImp());
            cellTotal.setCellStyle(currencyStyle);
            cellTotal.setCellFormula("SUM(I"+(i+2)+":J"+(i+2)+")");
            
            i++;
            
        }
        
        // Creamos una fila suma total en la hoja
        Row totalRow = pagina.createRow(listCFDI.size() + 1);
        Cell cellTotalBase = totalRow.createCell(8);
        cellTotalBase.setCellFormula("SUM(I2:I"+(listCFDI.size()+1)+")");
        cellTotalBase.setCellStyle(currencyBoldStyle);
        
        Cell cellTotalIva = totalRow.createCell(9);
        cellTotalIva.setCellFormula("SUM(J2:J"+(listCFDI.size()+1)+")");
        cellTotalIva.setCellStyle(currencyBoldStyle);
        
        Cell cellTotal = totalRow.createCell(10);
        cellTotal.setCellFormula("SUM(K2:K"+(listCFDI.size()+1)+")");
        cellTotal.setCellStyle(currencyBoldStyle);
        

        guardarArchivoXLSX(workbook);
    }

    private static Double setDecimal(String value) {        
        return new BigDecimal(value).doubleValue();
    }
   
    private static Map<String, String> setCellValues(Map<String, String> map) {

        Map<String, String> mapCellValues = new HashMap<>();
        String[] cellValues = {"nombreArchivo", "rfcEmisor", "nombreEmisor",
            "subTotal"};
//            "totalImpuestoTrasladados", "total", "trasladoIVA", "trasladoIEPS"};

        for (String key : cellValues) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (key.equals(entry.getKey())) {
                    mapCellValues.put(entry.getKey(), entry.getValue());
                    break;
                }
            }
            //System.out.format("Atributo : [ %s ], Requerido : [ %s ] \n", entry.getKey(), entry.getValue());
        }
        System.out.println("mapCellValues = " + mapCellValues.toString());
        return mapCellValues;
    }

    private static void guardarArchivoXLSX(Workbook workbook) {
        // Ahora guardaremos el archivo
        try {
            File xlsxFile = new File(Constantes.DIRECTORY + "\\" + label + ".xlsx");

            // Creamos el flujo de salida de datos,
            // apuntando al archivo donde queremos 
            // almacenar el libro de Excel
            FileOutputStream salida = new FileOutputStream(xlsxFile);

            // Almacenamos el libro de 
            // Excel via ese 
            // flujo de datos
            workbook.write(salida);

            // Cerramos el libro para concluir operaciones
            workbook.close();

            LOGGER.log(Level.INFO, "Archivo creado exitosamente en {0}", xlsxFile.getAbsolutePath());

        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Archivo no localizable en sistema de archivos");
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error de entrada/salida");
        }
    }

}
