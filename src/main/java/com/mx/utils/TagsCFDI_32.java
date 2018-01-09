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
public enum TagsCFDI_32 {
    
    COMPROBANTE(new String[]{"fecha", "formaDePago", "subTotal", "Moneda", "total", "metodoDePago", "LugarExpedicion"}),
    EMISOR(new String[]{"rfc", "nombre"}),
    RECEPTOR(new String[]{"rfc", "nombre"}),
    IMPUESTOS(new String[]{"totalImpuestosTrasladados"}),
    TRASLADO(new String[]{"impuesto", "tasa", "importe"});
    
    private final String[] arrayTags;

    private TagsCFDI_32(String[] arrayTags) {
        this.arrayTags = arrayTags;
    }
    
     public String[] getArrayTagsCFDI() {
        return arrayTags;
    }
    
}
