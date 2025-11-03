package com.sistdist.controlador;

import com.sistdist.interfaces.IServicioExclusionMutua;
import java.rmi.Naming;

public final class ClienteEM {
    private static volatile IServicioExclusionMutua ref;

    public static synchronized void reconectarConLiderActual() {
        String url = DirectorioCoordinador.urlActualEM();
        try {
            ref = (IServicioExclusionMutua) Naming.lookup(url);
            System.out.println("[ClienteEM] Conectado a EM: " + url);
        } catch (Exception e) {
            System.err.println("[ClienteEM] Error conectando a EM " + url + " -> " + e.getMessage());
            ref = null;
        }
    }

    public static boolean ping() {
        try {
            if (ref == null) return false;
            ref.ping();  // <-- ¡Mayúscula!
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static IServicioExclusionMutua servicio() { return ref; }
}
