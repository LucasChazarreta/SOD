package com.sistdist.sensortemperatura;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HiloSensadoTemperatura extends Thread {
    private boolean on;
    private double temperatura;
    private final Socket cnxServidor;
    private final PrintWriter pw;
    private final Random rand;

    public HiloSensadoTemperatura(Socket s, PrintWriter out) {
        this.cnxServidor = s;
        this.pw = out;
        this.on = false;
        this.rand = new Random();
    }

    private double generarTemperatura() {
        // Ejemplo: valores entre 10 y 35 grados
        return 10 + rand.nextDouble() * 25;
    }

    public void encender() { on = true; }
    public void apagar()   { on = false; }

    public double leerTemperatura() { return temperatura; }

@Override
public void run() {
    on = true;
    final long PERIOD_MS = 10_000L;
    long next = ((System.currentTimeMillis() / PERIOD_MS) + 1) * PERIOD_MS;

    while (on) {
        try {
            long wait = next - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);

            //temperatura = generarTemperatura();
            temperatura = 90;
            System.out.printf("[SENSOR-T] Generada temperatura: %.1f°C%n", temperatura);

            pw.println(temperatura);
            pw.flush();

            next += PERIOD_MS;
        } catch (InterruptedException ex) {
            Logger.getLogger(HiloSensadoTemperatura.class.getName()).log(Level.SEVERE, null, ex);
            on = false;
        }
    }
}

}
