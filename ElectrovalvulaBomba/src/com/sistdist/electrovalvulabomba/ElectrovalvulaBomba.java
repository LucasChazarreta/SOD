package com.sistdist.electrovalvulabomba;

import java.io.*;
import java.net.*;

public class ElectrovalvulaBomba {
    public static void main(String[] args) {
        int id = 6; // ID de la bomba
        try (Socket socket = new Socket("127.0.0.1", 20000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Handshake inicial
            out.println("electrovalvula;" + id);
            out.flush();
            System.out.println("[EV-BOMBA] Conectada al controlador como EV" + id);

            String comando;
            while ((comando = in.readLine()) != null) {
                if ("abrir".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-BOMBA] Bomba encendida.");
                    out.println("EV" + id + ":ok_abierta");
                } else if ("cerrar".equalsIgnoreCase(comando)) {
                    System.out.println("[EV-BOMBA] Bomba apagada.");
                    out.println("EV" + id + ":ok_cerrada");
                } else {
                    System.out.println("[EV-BOMBA] Comando desconocido: " + comando);
                    out.println("EV" + id + ":error");
                }
                out.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
