package com.sistdist.controlador;

/**
 * Arranca el servidor de Exclusión Mutua en este nodo
 * cuando el anillo lo declara LÍDER.
 */
public final class ArrancadorServidorEM {
    private static volatile boolean corriendo = false;
    private static volatile int puertoLevantado = -1;

    /** Levanta el EM del líder en el puerto indicado (si no está corriendo ya en ese puerto). */
    public static synchronized void iniciarSiNoEstaCorriendo(int puertoRmi) {
        if (corriendo && puertoLevantado == puertoRmi) return;
        try {
            com.sistdist.servidorexclusionmutua.ServerExclusionMutuaRMI.iniciar(puertoRmi);
            corriendo = true;
            puertoLevantado = puertoRmi;
            System.out.println("[EM] Levantado en puerto " + puertoRmi);
        } catch (Exception e) {
            System.err.println("[EM] Error al iniciar en " + puertoRmi + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Detiene el EM si está corriendo en ese puerto. */
    public static synchronized void detenerSiEsEste(int puertoRmi) {
        if (corriendo && puertoLevantado == puertoRmi) {
            try {
                com.sistdist.servidorexclusionmutua.ServerExclusionMutuaRMI.detener();
            } finally {
                corriendo = false;
                puertoLevantado = -1;
            }
        }
    }
}
