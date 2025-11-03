
package com.sistdist.sensorhumedad1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

//Hay cinco sensores de humedad, uno por cada parcela. Estos miden el porcentaje de humedad del suelo (0-100%). 
public class SensorHumedad1 {

    public static void main(String[] args) {
        InetAddress IPServidor;
        PrintWriter pw;
        
        try {
            IPServidor = InetAddress.getByName("127.0.0.1");
            Socket cliente = new Socket(IPServidor, 20000);
            pw = new PrintWriter(cliente.getOutputStream());
            // en el main, luego de crear socket y PrintWriter
            pw.println("sensorHumedad;1"); 
            pw.flush();
            HiloSensado sensor = new HiloSensado(cliente, pw);
            sensor.start();
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorHumedad1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SensorHumedad1.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
