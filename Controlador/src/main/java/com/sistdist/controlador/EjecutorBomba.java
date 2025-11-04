package com.sistdist.controlador;

import java.io.PrintWriter;

import com.sistdist.interfaces.IServicioExclusionMutua;

public class EjecutorBomba extends Thread {

    @Override
    public void run() {
        while (true) {
            try {
                Controlador.Orden ord = Controlador.colaOrdenes.take();
                switch (ord.tipo()) {
                    case RIEGO -> ejecutarRiego(ord);
                    case FERTI -> ejecutarFerti(ord);
                }
            } catch (InterruptedException e) { return; }
            catch (Exception e) {
                Controlador.logEvento("[EJECUTOR] ERROR: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
    }

    private void ejecutarRiego(Controlador.Orden ord) throws Exception {
        int parcela = ord.parcela();

        // Re-chequeo instantáneo
        Double H = Controlador.humedades.get(parcela);
        Double T = Controlador.temp;
        Double R = Controlador.rad;
        Boolean L = Controlador.lluvia;
        if (H == null || T == null || R == null || L == null) return;

        double inr = 0.5 * (1 - H / 100.0) + 0.3 * (T / 40.0) + 0.2 * (R / 1000.0);
        int mins = (!L && inr > 0.9) ? 10 : (!L && inr > 0.8) ? 7 : (!L && inr > 0.7) ? 5 : 0;
        if (mins == 0) {
            Controlador.logEvento(String.format(
                    "[RIEGO P%d] Cancelado (condiciones cambiaron)  INR=%.3f  Lluvia=%s%n",
                    parcela, inr, L ? "SI" : "NO"));
            return;
        }

        PrintWriter bomba = Controlador.conexionesEV.get(6);
        PrintWriter ev    = Controlador.conexionesEV.get(parcela);
        if (bomba == null || ev == null) {
            Controlador.logEvento(String.format("[RIEGO P%d] Cancelado (conexiones no disponibles)%n", parcela));
            return;
        }

        // >>> NUEVO: si está ocupado, avisar y reencolar sin bloquear
        if (Controlador.tokenEnUso.get()) {
            String dueno = (Controlador.duenoToken == null ? "otro proceso" : Controlador.duenoToken);
            Controlador.logEvento(String.format("[RIEGO P%d] No puede obtener token (ocupado por %s)\n", parcela, dueno));
            Controlador.colaOrdenes.offer(ord); // reintenta luego
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            return;
        }

        IServicioExclusionMutua em = Controlador.serverEM;
        if (em == null) {
            Controlador.logEvento(String.format("[RIEGO P%d] EM no disponible. Se reintentará.%n", parcela));
            ClienteEM.reconectarConLiderActual();
            Controlador.colaOrdenes.offer(ord);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[RIEGO P%d] Obtener token...\n", parcela));
        boolean tokenObtenido = false;

        em.ObtenerRecurso(Controlador.serverRMI);
        Controlador.semToken.acquireUninterruptibly();
        tokenObtenido = true;
        sb.append("[RIEGO] Token recibido\n");

        // marcar dueño
        Controlador.tokenEnUso.set(true);
        Controlador.duenoToken = String.format("RIEGO P%d", parcela);

        try {
            sb.append(String.format("[RIEGO] Regando Parcela %d durante %d min...\n", parcela, mins));
            bomba.println("abrir"); bomba.flush();
            ev.println("abrir");    ev.flush();

            // SIMULACIÓN en segundos; para minutos reales: mins * 60_000L
            Thread.sleep(8 * 1000L);
            //Thread.sleep(10_000L);
            ev.println("cerrar");   ev.flush();
            bomba.println("cerrar");bomba.flush();
            sb.append("[RIEGO] Riego finalizado\n");
        } finally {
            if (tokenObtenido) {
                try { em.DevolverRecurso(); } catch (Exception ignored) {}
            }
            sb.append("[RIEGO] Devolver token\n");
            Controlador.logEvento(sb.toString());

            // liberar dueño
            Controlador.tokenEnUso.set(false);
            Controlador.duenoToken = null;
        }
    }

    private void ejecutarFerti(Controlador.Orden ord) throws Exception {
        if (Boolean.TRUE.equals(Controlador.lluvia)) {
            Controlador.logEvento("[FERTIRRIGACION] Cancelada (lluvia actual)\n");
            return;
        }

        boolean condicionesFerti = false;
        for (int i = 1; i <= Controlador.NUM_PARCELAS && !condicionesFerti; i++) {
            Double H = Controlador.humedades.get(i);
            Double T = Controlador.temp;
            Double R = Controlador.rad;
            if (H == null || T == null || R == null) continue;
            double inr = 0.5 * (1 - H / 100.0) + 0.3 * (T / 40.0) + 0.2 * (R / 1000.0);
            if (H < 25.0 && inr < 0.5) condicionesFerti = true;
        }
        if (!condicionesFerti) {
            Controlador.logEvento("[FERTIRRIGACION] Cancelada (condiciones cambiaron)\n");
            return;
        }

        PrintWriter bomba = Controlador.conexionesEV.get(6);
        PrintWriter evF   = Controlador.conexionesEV.get(7);
        if (bomba == null || evF == null) {
            Controlador.logEvento("[FERTIRRIGACION] Cancelada (conexiones no disponibles)\n");
            return;
        }

        // >>> NUEVO: si está ocupado, avisar en EV-FERTI y reencolar sin bloquear
        if (Controlador.tokenEnUso.get()) {
            String dueno = (Controlador.duenoToken == null ? "otro proceso" : Controlador.duenoToken);
            // empujar mensaje a EV-FERTI
            evF.println("log|[FERTIRRIGACION] No puede obtener token (ocupado por " + dueno + ")");
            evF.flush();
            Controlador.colaOrdenes.offer(ord);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            return;
        }

        IServicioExclusionMutua em = Controlador.serverEM;
        if (em == null) {
            Controlador.logEvento("[FERTIRRIGACION] EM no disponible. Se reintentará.\n");
            ClienteEM.reconectarConLiderActual();
            Controlador.colaOrdenes.offer(ord);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            return;
        }

        // Obtener token
        evF.println("log|[FERTIRRIGACION] Obtener token..."); evF.flush();
        boolean tokenObtenido = false;

        em.ObtenerRecurso(Controlador.serverRMI);
        Controlador.semToken.acquireUninterruptibly();
        tokenObtenido = true;
        evF.println("log|[FERTIRRIGACION] Token recibido"); evF.flush();

        // marcar dueño
        Controlador.tokenEnUso.set(true);
        Controlador.duenoToken = "FERTIRRIGACION";

        try {
            evF.println("abrir"); evF.flush();
            evF.println("log|[FERTIRRIGACION] Iniciando..."); evF.flush();

            // SIMULACIÓN: ord.segundos
            Thread.sleep(ord.segundos() * 1000L);

            evF.println("cerrar"); evF.flush();
            evF.println("log|[FERTIRRIGACION] Finalizada"); evF.flush();
        } finally {
            if (tokenObtenido) {
                try { em.DevolverRecurso(); } catch (Exception ignored) {}
            }
            evF.println("log|[FERTIRRIGACION] Devolver token"); evF.flush();

            // liberar dueño
            Controlador.tokenEnUso.set(false);
            Controlador.duenoToken = null;
        }
    }
}
