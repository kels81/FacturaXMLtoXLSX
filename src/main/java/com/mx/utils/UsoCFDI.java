package com.mx.utils;

public enum UsoCFDI {
    G01("G01", "Adquisición de mercancías"),
    G02("G02", "Devoluciones, descuentos o bonificaciones"),
    G03("G03", "Gastos en general"),
    I01("I01", "Construcciones"),
    I02("I02", "Mobiliario y equipo de oficina por inversiones"),
    I03("I03", "Equipo de transporte"),
    I04("I04", "Equipo de computo y accesorios"),
    I05("I05", "Dados, troqueles, moldes, matrices y herramental"),
    I06("I06", "Comunicaciones telefónicas"),
    I07("I07", "Comunicaciones satelitales"),
    I08("I08", "Otra maquinaria y equipo"),
    D01("D01", "Honorarios médicos, dentales y gastos hospitalarios"),
    D02("D02", "Gastos médicos por incapacidad o discapacidad"),
    D03("D03", "Gastos funerales"),
    D04("D04", "Donativos"),
    D05("D05", "Intereses reales efectivamente pagados por créditos hipotecarios (casa habitación)"),
    D06("D06", "Aportaciones voluntarias al SAR"),
    D07("D07", "Primas por seguros de gastos médicos"),
    D08("D08", "Gastos de transportación escolar obligatoria"),
    D09("D09", "Depósitos en cuentas para el ahorro, primas que tengan como base planes de pensiones"),
    D10("D10", "Pagos por servicios educativos (colegiaturas)"),
    S01("S01", "Sin efectos fiscales"),
    CP01("CP01", "Pagos");

    private final String clave;
    private final String descripcion;

    UsoCFDI(String clave, String descripcion) {
        this.clave = clave;
        this.descripcion = descripcion;
    }

    public String getClave() {
        return clave;
    }

    public String getDescripcion() {
        return descripcion;
    }

    // Método para obtener el UsoCFDI por clave
    public static UsoCFDI fromClave(String clave) {
        for (UsoCFDI uso : values()) {
            if (uso.clave.equalsIgnoreCase(clave)) {
                return uso;
            }
        }
        return null; // Retorna null si no se encuentra la clave
    }

    // Método para obtener la descripción de un UsoCFDI
    public static String getDescripcionByClave(String clave) {
        UsoCFDI uso = fromClave(clave);
        return (uso != null) ? uso.getDescripcion() : "Clave no encontrada";
    }
}
