/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mx.bean;

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
    private String usoCFDI;
    private String formaDePago;
    private String metodoDePago;
    private String tipoDeComprobante;
    private String subTotal;
    private String descuento;
    private String total;
    private String totalImpuestoTrasladados;
    private String baseIVA0;
    private String tipoFactor;
    private String fechaPago;
    private String estatusPago;

    private String trasladoIVAOriginal;
    private String trasladoIVA;
    private String tasaIVA;

    private String trasladoIEPS;
    private String tasaIEPS;

    private String uuidPropio;
    private String uuidReferencia;

    private Double base;
    private Double iva;
    private Double totalImp;


    
    public CFDI() { }

    public CFDI(String nombreArchivo, String fecha, String lugarExpedicion, String rfcEmisor, String nombreEmisor, String rfcReceptor, String nombreReceptor, String moneda, String usoCFDI, String formaDePago, String tipoDeComprobante, String metodoDePago, String subTotal, String total, String descuento, String baseIVA0, String tipoFactor) {
        this.nombreArchivo = nombreArchivo;
        this.fecha = fecha;
        this.lugarExpedicion = lugarExpedicion;
        this.rfcEmisor = rfcEmisor;
        this.nombreEmisor = nombreEmisor;
        this.rfcReceptor = rfcReceptor;
        this.nombreReceptor = nombreReceptor;
        this.moneda = moneda;
        this.usoCFDI = usoCFDI;
        this.formaDePago = formaDePago;
        this.tipoDeComprobante = tipoDeComprobante;
        this.metodoDePago = metodoDePago;
        this.subTotal = subTotal;
        this.total = total;
        this.descuento = descuento;
        this.baseIVA0 = baseIVA0;
        this.tipoFactor = tipoFactor;
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

    public String getUsoCFDI() { return usoCFDI; }

    public void setUsoCFDI(String usoCFDI) { this.usoCFDI = usoCFDI; }

    public String getFormaDePago() {
        return formaDePago;
    }

    public void setFormaDePago(String formaDePago) {
        this.formaDePago = formaDePago;
    }

    public String getTipoDeComprobante() { return tipoDeComprobante; }

    public void setTipoDeComprobante(String tipoDeComprobante) { this.tipoDeComprobante = tipoDeComprobante; }

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

    public void setTotalImpuestoTrasladados(String totalImpuestoTrasladados) { this.totalImpuestoTrasladados = totalImpuestoTrasladados; }

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
    
    public Double getBase() {
        return base;
    }

    public void setBase(Double base) {
        this.base = base;
    }

    public Double getIva() {
        return iva;
    }

    public void setIva(Double iva) {
        this.iva = iva;
    }

    public Double getTotalImp() {
        return totalImp;
    }

    public void setTotalImp(Double totalImp) {
        this.totalImp = totalImp;
    }

    public String getDescuento() {
        return descuento;
    }

    public void setDescuento(String descuento) {
        this.descuento = descuento;
    }

    public String getBaseIVA0() {
        return baseIVA0;
    }

    public void setBaseIVA0(String baseIVA0) {
        this.baseIVA0 = baseIVA0;
    }

    public String getTipoFactor() {
        return tipoFactor;
    }

    public void setTipoFactor(String tipoFactor) {
        this.tipoFactor = tipoFactor;
    }

    public String getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(String fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getTrasladoIVAOriginal() {
        return trasladoIVAOriginal;
    }

    public void setTrasladoIVAOriginal(String trasladoIVAOriginal) {
        this.trasladoIVAOriginal = trasladoIVAOriginal;
    }

    public String getUuidPropio() {
        return uuidPropio;
    }

    public void setUuidPropio(String uuidPropio) {
        this.uuidPropio = uuidPropio;
    }

    public String getUuidReferencia() {
        return uuidReferencia;
    }

    public void setUuidReferencia(String uuidReferencia) {
        this.uuidReferencia = uuidReferencia;
    }

    public String getEstatusPago() {
        return estatusPago;
    }

    public void setEstatusPago(String estatusPago) {
        this.estatusPago = estatusPago;
    }
}