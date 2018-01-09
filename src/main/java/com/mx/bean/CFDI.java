/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mx.bean;

import java.util.Map;

/**
 *
 * @author Edrd
 */
public class CFDI {
    
    private String nombreArchivo;
    private String fecha;
    private String lugarExpedicion;
    private String rfcEmisor;
    private String nombreEmisor;
    private String rfcReceptor;
    private String nombreReceptor;
    private String moneda;
    private String formaDePago;
    private String metodoDePago;
    private String subTotal;
    private String total;
    private String totalImpuestoTrasladados;
    private String trasladoIVA;
    private String tasaIVA;
    private String trasladoIEPS;
    private String tasaIEPS;
    
    public CFDI() {
        
    }

    public CFDI(String nombreArchivo, String fecha, String lugarExpedicion, String rfcEmisor, String nombreEmisor, String rfcReceptor, String nombreReceptor, String moneda, String formaDePago, String metodoDePago, String subTotal, String total) {
        this.nombreArchivo = nombreArchivo;
        this.fecha = fecha;
        this.lugarExpedicion = lugarExpedicion;
        this.rfcEmisor = rfcEmisor;
        this.nombreEmisor = nombreEmisor;
        this.rfcReceptor = rfcReceptor;
        this.nombreReceptor = nombreReceptor;
        this.moneda = moneda;
        this.formaDePago = formaDePago;
        this.metodoDePago = metodoDePago;
        this.subTotal = subTotal;
        this.total = total;
    }
    
    

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getLugarExpedicion() {
        return lugarExpedicion;
    }

    public void setLugarExpedicion(String lugarExpedicion) {
        this.lugarExpedicion = lugarExpedicion;
    }

    public String getRfcEmisor() {
        return rfcEmisor;
    }

    public void setRfcEmisor(String rfcEmisor) {
        this.rfcEmisor = rfcEmisor;
    }

    public String getNombreEmisor() {
        return nombreEmisor;
    }

    public void setNombreEmisor(String nombreEmisor) {
        this.nombreEmisor = nombreEmisor;
    }

    public String getRfcReceptor() {
        return rfcReceptor;
    }

    public void setRfcReceptor(String rfcReceptor) {
        this.rfcReceptor = rfcReceptor;
    }

    public String getNombreReceptor() {
        return nombreReceptor;
    }

    public void setNombreReceptor(String nombreReceptor) {
        this.nombreReceptor = nombreReceptor;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getFormaDePago() {
        return formaDePago;
    }

    public void setFormaDePago(String formaDePago) {
        this.formaDePago = formaDePago;
    }

    public String getMetodoDePago() {
        return metodoDePago;
    }

    public void setMetodoDePago(String metodoDePago) {
        this.metodoDePago = metodoDePago;
    }

    public String getSubTotal() {
        return subTotal;
    }

    public void setSubTotal(String subTotal) {
        this.subTotal = subTotal;
    }

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }

    public String getTotalImpuestoTrasladados() {
        return totalImpuestoTrasladados;
    }

    public void setTotalImpuestoTrasladados(String totalImpuestoTrasladados) {
        this.totalImpuestoTrasladados = totalImpuestoTrasladados;
    }

    public String getTrasladoIVA() {
        return trasladoIVA;
    }

    public void setTrasladoIVA(String trasladoIVA) {
        this.trasladoIVA = trasladoIVA;
    }

    public String getTasaIVA() {
        return tasaIVA;
    }

    public void setTasaIVA(String tasaIVA) {
        this.tasaIVA = tasaIVA;
    }

    public String getTrasladoIEPS() {
        return trasladoIEPS;
    }

    public void setTrasladoIEPS(String trasladoIEPS) {
        this.trasladoIEPS = trasladoIEPS;
    }

    public String getTasaIEPS() {
        return tasaIEPS;
    }

    public void setTasaIEPS(String tasaIEPS) {
        this.tasaIEPS = tasaIEPS;
    }

}