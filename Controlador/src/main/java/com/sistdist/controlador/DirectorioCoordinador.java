package com.sistdist.controlador;

public final class DirectorioCoordinador {
    private static volatile long uidLider = -1;
    private static volatile String urlActual = "rmi://localhost:10000/servidorCentralEM";

    public static void actualizarDesdeUidLider(long uid) {
        uidLider = uid;
        int puerto = PuertoEM.porUid(uid);
        urlActual = "rmi://localhost:" + puerto + "/servidorCentralEM";
        System.out.println("[DirectorioCoordinador] Nuevo EM actual = " + urlActual);
    }

    public static long uidLiderActual() { return uidLider; }

    public static String urlActualEM() { return urlActual; }
}
