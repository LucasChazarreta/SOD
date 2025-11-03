/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.sistdist.sensorhumedad4;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lesca
 */
public class SensorHumedad4 {

    public static void main(String[] args) {
        InetAddress IPServidor;
        PrintWriter pw;
        try {
            IPServidor = InetAddress.getByName("127.0.0.1");
            Socket cliente = new Socket(IPServidor, 20000);
            pw = new PrintWriter(cliente.getOutputStream());
            pw.println("sensorHumedad;4");
            pw.flush();
            HiloSensado sensor = new HiloSensado(cliente, pw);
            sensor.start();
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorHumedad4.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SensorHumedad4.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
