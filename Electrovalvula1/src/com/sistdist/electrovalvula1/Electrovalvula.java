package com.sistdist.electrovalvula1;

import java.io.*;
import java.net.*;

public class Electrovalvula {
    public static void main(String[] args) {
        int id = 1; 
        try (Socket socket = new Socket("127.0.0.1", 20000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Handshake inicial
            out.println("electrovalvula;" + id);
            System.out.println("[EV1] Handshake enviado: electrovalvula;1");
            out.flush();
            System.out.println("[EV" + id + "] Conectada al controlador.");

            String comando;
            while ((comando = in.readLine()) != null) {
                if ("abrir".equals(comando)) {
                    System.out.println("[EV" + id + "] Válvula abierta.");
                    out.println("EV" + id + ":ok_abierta");   // confirmación al controlador
                } else if ("cerrar".equals(comando)) {
                    System.out.println("[EV" + id + "] Válvula cerrada.");
                    out.println("EV" + id + ":ok_cerrada");   // confirmación al controlador
                } else {
                    System.out.println("[EV" + id + "] Comando desconocido: " + comando);
                    out.println("EV" + id + ":error_comando");
                }
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
