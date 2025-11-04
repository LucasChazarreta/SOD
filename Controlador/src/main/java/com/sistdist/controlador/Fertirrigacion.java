package com.sistdist.controlador;

import java.io.PrintWriter;
import java.util.Random;

import com.sistdist.interfaces.IServicioExclusionMutua;

public class Fertirrigacion extends Thread {

    private static final int INTERVALO_MIN_S = 30;//5 20
    private static final int INTERVALO_MAX_S = 40;//10 40
    private static final int DURACION_FERTI_S = 40; // 30 5 aumentá para retener token

    private final Random rnd = new Random();

    public Fertirrigacion() {
        setName("FertirrigacionAuto");
        setDaemon(true);
    }

    @Override
    public void run() {
        dormir(randomMs(INTERVALO_MIN_S, INTERVALO_MAX_S));
        while (true) {
            intentarUnaVez();
            dormir(randomMs(INTERVALO_MIN_S, INTERVALO_MAX_S));
        }
    }

    private void intentarUnaVez() {
        if (Boolean.TRUE.equals(Controlador.lluvia)) return;
        PrintWriter bomba = Controlador.conexionesEV.get(6);
        PrintWriter evF   = Controlador.conexionesEV.get(7);
        if (bomba == null || evF == null) return;
        if (!Controlador.colaOrdenes.isEmpty()) return; // evitar competir con riego encolado

        IServicioExclusionMutua em = Controlador.serverEM;
        if (em == null) {
            logToEVFerti("[FERTIRRIGACION] EM no disponible. Reintentando...");
            ClienteEM.reconectarConLiderActual();
            em = Controlador.serverEM;
            if (em == null) {
                return;
            }
        }

        boolean tokenObtenido = false;

        try {
            logToEVFerti("[FERTIRRIGACION] Intento automatico...");
            logToEVFerti("[FERTIRRIGACION] Obtener token...");
            em.ObtenerRecurso(Controlador.serverRMI);
            Controlador.semToken.acquireUninterruptibly();
            tokenObtenido = true;
            logToEVFerti("[FERTIRRIGACION] Token recibido");

            // marcar dueño
            Controlador.tokenEnUso.set(true);
            Controlador.duenoToken = "FERTIRRIGACION";

            if (Boolean.TRUE.equals(Controlador.lluvia)) {
                em.DevolverRecurso();
                logToEVFerti("[FERTIRRIGACION] Devolver token");
                Controlador.tokenEnUso.set(false);
                Controlador.duenoToken = null;
                return;
            }

            evF.println("abrir"); evF.flush();
            logToEVFerti("[FERTIRRIGACION] fertirrigando...");
            Thread.sleep(DURACION_FERTI_S * 1000L);
            evF.println("cerrar"); evF.flush();
            logToEVFerti("[FERTIRRIGACION] fertirriego finalizado");

        } catch (Exception e) {
            logToEVFerti("[FERTIRRIGACION] ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tokenObtenido) {
                try { em.DevolverRecurso(); } catch (Exception ignored) {}
            }
            logToEVFerti("[FERTIRRIGACION] Devolver token");
            Controlador.tokenEnUso.set(false);
            Controlador.duenoToken = null;
        }
    }

    private void logToEVFerti(String linea) {
        PrintWriter evF = Controlador.conexionesEV.get(7);
        if (evF != null && linea != null) {
            evF.println("log|" + linea);
            evF.flush();
        }
    }

    private void dormir(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private long randomMs(int minS, int maxS) {
        int s = minS + rnd.nextInt(Math.max(1, maxS - minS + 1)); return s * 1000L;
    }
}
