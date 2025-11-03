
package com.sistdist.sensorhumedad2;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SensorHumedad2 {

    public static void main(String[] args) {
        InetAddress IPServidor;
        PrintWriter pw;
        try {
            IPServidor = InetAddress.getByName("127.0.0.1");
            Socket cliente = new Socket(IPServidor, 20000);
            pw = new PrintWriter(cliente.getOutputStream());
            pw.println("sensorHumedad;2"); // en el #1
                        // en el #2 -> "sensorHumedad;2", etc.
            pw.flush();
            HiloSensado sensor = new HiloSensado(cliente, pw);
            sensor.start();
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorHumedad2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SensorHumedad2.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
