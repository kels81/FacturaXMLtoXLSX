/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mx.utils;

/**
 *
 * @author Edrd
 */
public enum TagsCFDI_33 {
    
    COMPROBANTE(new String[]{"Fecha", "FormaPago", "SubTotal", "Moneda", "Total", "MetodoPago", "LugarExpedicion"}),
    EMISOR(new String[]{"Rfc", "Nombre"}),
    RECEPTOR(new String[]{"Rfc", "Nombre"}),
    IMPUESTOS(new String[]{"TotalImpuestosTrasladados"}),
    TRASLADO(new String[]{"Impuesto", "TasaOCuota", "Importe"});
    
    private final String[] arrayTags;

    private TagsCFDI_33(String[] arrayTags) {
        this.arrayTags = arrayTags;
    }
    
     public String[] getArrayTagsCFDI() {
        return arrayTags;
    }
    
}
