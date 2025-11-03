
package com.sistdist.sensorhumedad1;

import java.io.PrintWriter;
import java.lang.Math;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lesca
 */
public class HiloSensado extends Thread {
    
    private boolean on;
    private double humedad;
    Socket cnxServidor;
    PrintWriter pw;
    
    public HiloSensado(Socket s, PrintWriter imp){
        on = false;
        cnxServidor = s;
        pw = imp;
    }
    
 public double generarHumedad() {
    // 50% de probabilidad de generar un valor menor a 50
    if (Math.random() < 0.5) {
        // Genera un valor entre 0 y 50 (activará la fertirrigación)
        return Math.random() * 50;
    } else {
        // Genera un valor entre 50 y 100 (no activará la fertirrigación)
        return 50 + Math.random() * 50;
    }
}
    
    public void encender(){
        on = true;
    }
    
    public void apagar(){
        on = false;
    }
    
    public double leerHumedad(){
        return humedad;
    }    
    
@Override
public void run() {
    on = true;
    final long PERIOD_MS = 10_000L;
    long next = ((System.currentTimeMillis() / PERIOD_MS) + 1) * PERIOD_MS;

    while (on) {
        try {
            long wait = next - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);

            //humedad = generarHumedad();
            double humedad = 10;
            System.out.println(humedad);

            pw.println(humedad);
            pw.flush();

            next += PERIOD_MS; // siguiente disparo exacto
        } catch (InterruptedException ex) {
            Logger.getLogger(HiloSensado.class.getName()).log(Level.SEVERE, null, ex);
            on = false;
        }
    }
}

}
