package com.sistdist.sensorlluvia;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiloSensadoLluvia extends Thread {
    private final Socket conexion;
    private final PrintWriter out;
    private final Random rand = new Random();
    private boolean on = true;

    public HiloSensadoLluvia(Socket s, PrintWriter pw) {
        this.conexion = s;
        this.out = pw;
    }

@Override
public void run() {
    final long PERIOD_MS = 10_000L;
    long next = ((System.currentTimeMillis() / PERIOD_MS) + 1) * PERIOD_MS;
    on = true;

    while (on) {
        try {
            long wait = next - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);

            // 20% prob. lluvia (o dejá fijo en 0 si querés que no llueva)
            // int valor = rand.nextDouble() < 0.2 ? 1 : 0;
            int valor = 0; // 0 para que no llueva y deje regar
            out.println(valor);
            out.flush();
            System.out.println("[SensorLluvia] Enviado = " + valor);

            next += PERIOD_MS;
        } catch (InterruptedException ex) {
            Logger.getLogger(HiloSensadoLluvia.class.getName()).log(Level.SEVERE, null, ex);
            on = false;
        }
    }
}

}
