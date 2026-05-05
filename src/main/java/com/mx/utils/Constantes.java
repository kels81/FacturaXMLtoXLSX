package com.mx.utils;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;

public class Constantes {

    // Nombre de la carpeta raíz
    private static final String ROOT_FOLDER = "Facturas 2026";

    // Ruta base para búsqueda histórica en OneDrive
    private static final String ROOT_FOLDER_HISTORICO = "FACTURAS RECIBIDAS 2026";
    private static final String DIRECTORY_HISTORICO = "C:\\Users\\Edrd\\OneDrive\\SAT\\NILA 2026\\" + ROOT_FOLDER_HISTORICO;

    // Mapeo de meses para evitar el uso de un array con índices numéricos
    private static final Map<Integer, String> MESES = new HashMap<>();

    static {
        // Inicializar el mapa de meses
        MESES.put(1, "01_ENERO");
        MESES.put(2, "02_FEBRERO");
        MESES.put(3, "03_MARZO");
        MESES.put(4, "04_ABRIL");
        MESES.put(5, "05_MAYO");
        MESES.put(6, "06_JUNIO");
        MESES.put(7, "07_JULIO");
        MESES.put(8, "08_AGOSTO");
        MESES.put(9, "09_SEPTIEMBRE");
        MESES.put(10, "10_OCTUBRE");
        MESES.put(11, "11_NOVEMBRE");
        MESES.put(12, "12_DICIEMBRE");
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

    // Mes histórico en OneDrive
    public static String getHistoricDirectoryForMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
        }
        return DIRECTORY_HISTORICO + "\\" + MESES.get(month);
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