package com.mx.utils;

public enum MetodoPago {
    PPD("PPD", "Pago en Parcialidades o Diferido"),
    PUE("PUE", "Pago en Una sola Exhibición");

    private final String clave;
    private final String descripcion;

    MetodoPago(String clave, String descripcion) {
        this.clave = clave;
        this.descripcion = descripcion;
    }

    public String getClave() {
        return clave;
    }

    public String getDescripcion() {
        return descripcion;
    }

    // Método para obtener el MetodoPago por clave
    public static MetodoPago fromClave(String clave) {
        for (MetodoPago metodo : values()) {
            if (metodo.clave.equalsIgnoreCase(clave)) {
                return metodo;
            }
        }
        return null; // Retorna null si la clave no existe
    }

    // Método para obtener la descripción de un MetodoPago
    public static String getDescripcionByClave(String clave) {
        MetodoPago metodo = fromClave(clave);
        return (metodo != null) ? metodo.getDescripcion() : "Clave no encontrada";
    }
}
