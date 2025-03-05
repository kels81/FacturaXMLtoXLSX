package com.mx.utils;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;

public class Constantes {

    // Nombre de la carpeta raíz
    private static final String ROOT_FOLDER = "Facturas 2025";

    // Mapeo de meses para evitar el uso de un array con índices numéricos
    private static final Map<Integer, String> MESES = new HashMap<>();

    static {
        // Inicializar el mapa de meses
        MESES.put(1, "01_Enero");
        MESES.put(2, "02_Febrero");
        MESES.put(3, "03_Marzo");
        MESES.put(4, "04_Abril");
        MESES.put(5, "05_Mayo");
        MESES.put(6, "06_Junio");
        MESES.put(7, "07_Julio");
        MESES.put(8, "08_Agosto");
        MESES.put(9, "09_Septiembre");
        MESES.put(10, "10_Octubre");
        MESES.put(11, "11_Noviembre");
        MESES.put(12, "12_Diciembre");
    }

    // Ruta base del directorio
    private static final String DIRECTORY = "C:\\Users\\Edrd\\Documents\\_OTROS\\Facturacion\\" + ROOT_FOLDER;

    // Método para obtener la ruta del directorio de un mes específico
    public static String getDirectoryForMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
        }
        return DIRECTORY + "\\" + MESES.get(month);
    }

    // Método para obtener la ruta del directorio del mes actual
    public static String getDirectoryForCurrentMonth() {
        int currentMonth = Month.from(java.time.LocalDate.now()).getValue();
        return getDirectoryForMonth(currentMonth);
    }

    // Método para obtener el nombre del mes
    public static String getMonthName(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
        }
        return MESES.get(month).substring(3); // Elimina el prefijo numérico (por ejemplo, "01_Enero" -> "Enero")
    }
}