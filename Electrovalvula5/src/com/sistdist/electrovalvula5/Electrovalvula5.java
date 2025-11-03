
package com.sistdist.electrovalvula5;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Electrovalvula5 {
    public static void main(String[] args) {
    int id = 5; // identificador de la primera electrovalvula
        try {
            Socket socket = new Socket("127.0.0.1",20000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            
            // Identificacion ante el controlador
            out.println("electrovalvula;5");
            
            String comando;
            while ((comando = in.readLine()) != null) {
                if (comando.equals("abrir")) {
                    System.out.println("[EV" + id + "] Válvula abierta, bomba activada.");
                } else if (comando.equals("cerrar")) {
                    System.out.println("[EV" + id + "] Válvula cerrada, bomba detenida.");
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(Electrovalvula5.class.getName()).log(Level.SEVERE, null, ex);
        }
       
    }
    
}
