package com.mx.utils;

public enum FormaPago {
    EFECTIVO("01", "Efectivo"),
    CHEQUE_NOMINATIVO("02", "Cheque nominativo"),
    TRANSFERENCIA_ELECTRONICA("03", "Transferencia electrónica de fondos"),
    TARJETA_CREDITO("04", "Tarjeta de crédito"),
    MONEDERO_ELECTRONICO("05", "Monedero electrónico"),
    DINERO_ELECTRONICO("06", "Dinero electrónico"),
    VALES_DESPENSA("08", "Vales de despensa"),
    DACION_EN_PAGO("12", "Dación en pago"),
    PAGO_SUBROGACION("13", "Pago por subrogación"),
    PAGO_CONSIGNACION("14", "Pago por consignación"),
    CONDONACION("15", "Condonación"),
    COMPENSACION("17", "Compensación"),
    NOVACION("23", "Novación"),
    CONFUSION("24", "Confusión"),
    REMISION_DEUDA("25", "Remisión de deuda"),
    PRESCRIPCION_CADUCIDAD("26", "Prescripción o caducidad"),
    SATISFACCION_ACREEDOR("27", "A satisfacción del acreedor"),
    TARJETA_DEBITO("28", "Tarjeta de débito"),
    TARJETA_SERVICIOS("29", "Tarjeta de servicios"),
    APLICACION_ANTICIPOS("30", "Aplicación de anticipos"),
    INTERMEDIARIO_PAGOS("31", "Intermediario de pagos"),
    POR_DEFINIR("99", "Por definir");

    private final String clave;
    private final String descripcion;

    FormaPago(String clave, String descripcion) {
        this.clave = clave;
        this.descripcion = descripcion;
    }

    public String getClave() {
        return clave;
    }

    public String getDescripcion() {
        return descripcion;
    }

    // Método para obtener la FormaPago por clave
    public static FormaPago fromClave(String clave) {
        for (FormaPago forma : values()) {
            if (forma.clave.equalsIgnoreCase(clave)) {
                return forma;
            }
        }
        return null; // Retorna null si la clave no existe
    }

    // Método para obtener la descripción por clave
    public static String getDescripcionByClave(String clave) {
        FormaPago forma = fromClave(clave);
        return (forma != null) ? forma.getDescripcion() : "Clave no encontrada";
    }
}
