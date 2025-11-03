package com.sistdist.sensorradiacion;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiloSensadoRadiacion extends Thread {
    private boolean on;
    private double radiacion;
    private final Socket cnxServidor;
    private final PrintWriter pw;
    private final Random rand;

    public HiloSensadoRadiacion(Socket s, PrintWriter out) {
        this.cnxServidor = s;
        this.pw = out;
        this.on = false;
        this.rand = new Random();
    }

    private double generarRadiacion() {
        // Ejemplo: valores entre 0 y 1000 W/m²
        return rand.nextDouble() * 1000;
    }

    public void encender() { on = true; }
    public void apagar()   { on = false; }

    public double leerRadiacion() { return radiacion; }

@Override
public void run() {
    on = true;
    final long PERIOD_MS = 10_000L;
    long next = ((System.currentTimeMillis() / PERIOD_MS) + 1) * PERIOD_MS;

    while (on) {
        try {
            long wait = next - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);

            //radiacion = generarRadiacion();
            radiacion = 900;
            System.out.printf("[SENSOR-R] Generada radiación: %.0f W/m²%n", radiacion);

            pw.println(radiacion);
            pw.flush();

            next += PERIOD_MS;
        } catch (InterruptedException ex) {
            Logger.getLogger(HiloSensadoRadiacion.class.getName()).log(Level.SEVERE, null, ex);
            on = false;
        }
    }
}

}
