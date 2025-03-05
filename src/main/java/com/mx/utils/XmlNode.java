package com.mx.utils;

public enum XmlNode {
    CFDI_COMPROBANTE("cfdi:Comprobante", new String[]{
            "Certificado", "Exportacion", "Fecha", "Folio", "FormaPago",
            "LugarExpedicion", "MetodoPago", "Moneda", "NoCertificado",
            "Sello", "SubTotal", "TipoDeComprobante", "Total", "Version",
            "xmlns:cfdi", "xmlns:xsi", "xsi:schemaLocation"
    }),

    CFDI_EMISOR("cfdi:Emisor", new String[]{
            "Nombre", "RegimenFiscal", "Rfc"
    }),

    CFDI_RECEPTOR("cfdi:Receptor", new String[]{
            "DomicilioFiscalReceptor", "Nombre", "RegimenFiscalReceptor",
            "Rfc", "UsoCFDI"
    }),

    CFDI_CONCEPTOS("cfdi:Conceptos", new String[]{}),

    CFDI_CONCEPTO("cfdi:Concepto", new String[]{
            "Cantidad", "ClaveProdServ", "ClaveUnidad", "Descripcion",
            "Importe", "ObjetoImp", "Unidad", "ValorUnitario"
    }),

    CFDI_IMPUESTOS("cfdi:Impuestos", new String[]{
            "TotalImpuestosTrasladados"
    }),

    CFDI_TRASLADOS("cfdi:Traslados", new String[]{}),

    CFDI_TRASLADO("cfdi:Traslado", new String[]{
            "Base", "Importe", "Impuesto", "TasaOCuota", "TipoFactor"
    }),

    CFDI_COMPLEMENTO("cfdi:Complemento", new String[]{}),

    TFD_TIMBREFISCALDIGITAL("tfd:TimbreFiscalDigital", new String[]{
            "FechaTimbrado", "NoCertificadoSAT", "RfcProvCertif",
            "SelloCFD", "SelloSAT", "UUID", "Version",
            "xmlns:tfd", "xmlns:xsi", "xsi:schemaLocation"
    });

    private final String nodeName;
    private final String[] attributes;

    XmlNode(String nodeName, String[] attributes) {
        this.nodeName = nodeName;
        this.attributes = attributes;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String[] getAttributes() {
        return attributes;
    }

    // Método para obtener un XmlNode a partir del nombre del nodo en el XML
    public static XmlNode fromNodeName(String nodeName) {
        for (XmlNode node : values()) {
            if (node.nodeName.equalsIgnoreCase(nodeName)) {
                return node;
            }
        }
        return null;
    }
}
