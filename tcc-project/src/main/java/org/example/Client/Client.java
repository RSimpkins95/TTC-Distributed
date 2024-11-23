package org.example.Client;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client implements Runnable {
    private int clientId;
    private boolean assigned;
    private int assignment; // Top choice of this client
    private boolean active;
    private CopyOnWriteArrayList<Integer> wishlist;
    private Set<Integer> cycleSet;
    private int ackCount;
    private ConcurrentHashMap<Integer, InetAddress> clientAddresses;
    private DatagramSocket socket;
    private int remainingClients;
    private static final int BASE_PORT = 5000;

    public Client(int clientId, List<Integer> wishlist, Map<Integer, InetAddress> clientAddresses) throws SocketException {
        this.clientId = clientId;
        this.active = false;
        this.assignment = wishlist.isEmpty() ? -1 : wishlist.get(0);
        this.assigned = false;
        this.wishlist = new CopyOnWriteArrayList<>(wishlist);
        this.clientAddresses = new ConcurrentHashMap<>(clientAddresses);
        this.socket = new DatagramSocket(getClientPort(clientId));
        this.ackCount = 0;
        this.remainingClients = 0; // init later
        this.cycleSet = new HashSet<>();
    }

    @Override
    public void run() {
        try {
            new Thread(this::receiveMessages).start();
            remainingClients = clientAddresses.size();
            while (!assigned) {
                if(!active){
                    active = true;
                    executeCycleDetection();
                }

                Thread.sleep(1000); // Simulate delay between stages
            }

            System.out.println(assignment);
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void executeCycleDetection(){
        if(!wishlist.isEmpty()){
            int targetID = wishlist.get(0);
            sendMessage("CYCLE_CHECK|" + clientId + "|" + targetID, targetID);
        }
    }

    private void executeCycleCheck(String[] tokens){
        if(!active) return;
        if(tokens.length < 2) return;
        int messageID = Integer.parseInt(tokens[1]);

        if(messageID == clientId){
            ackCount = 0;

            if(tokens.length < 3) return;

            for(int i = 2; i < tokens.length; i++){
                int targetID = Integer.parseInt(tokens[i]);
                cycleSet.add(targetID);
            }

            broadcastMessage("CYCLE_STOP|" + clientId + "|" + cycleSet.size());
        }else{
            String[] updatedTokens = new String[tokens.length + 1];
            System.arraycopy(tokens, 0, updatedTokens, 0, tokens.length);
            updatedTokens[updatedTokens.length - 1] = wishlist.get(0).toString();
            String message = String.join("|", updatedTokens);
            sendMessage(message, wishlist.get(0));
        }
    }

    private void receiveMessages() {
        byte[] buffer = new byte[1024];
        while (!assigned) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                handleMessage(received);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(String message) throws IOException {
        String[] tokens = message.split("\\|");
        switch (tokens[0]) {
            case "REMOVE":
                handleRemove(tokens);
                break;
            case "CYCLE_CONFIRM":
                handleCycleConfirm();
                break;
            case "CYCLE_STOP":
                handleStop(tokens);
                break;
            case "CYCLE_CHECK":
                executeCycleCheck(tokens);
                break;
            case "ACK":
                handleAck();
                break;
            default:
                // This message typically only delivers the information
                // when looking for cycles. Commenting out for clarity in log
                // System.out.println("Unknown message: " + message);
        }
    }

    private void handleAck(){
        ackCount++;
        if(ackCount == remainingClients || remainingClients == 0){
            for(int targetID : cycleSet){
                sendMessage("CYCLE_CONFIRM", targetID);
            }
        }
    }

    private void handleStop(String tokens[]){
        if (tokens.length < 3) return;

        if(active){
            int sender = Integer.parseInt(tokens[1]);
            sendMessage("ACK", sender);
            int cycleSize = Integer.parseInt(tokens[2]);
            remainingClients = remainingClients - cycleSize;
            active = false;
        }
    }

    private void handleRemove(String[] tokens) {
        if (tokens.length < 2) return;
        int removedHouse = Integer.parseInt(tokens[1]);
        wishlist.remove(Integer.valueOf(removedHouse));
    }

    private void handleCycleConfirm() {
        assigned = true;
        assignment = wishlist.get(0);
        broadcastMessage("REMOVE|" + assignment);
    }

    private void broadcastMessage(String message) {
        for (int id : clientAddresses.keySet()) {
            sendMessage(message, id);
        }
    }

    private void sendMessage(String message, Integer targetId) {
        try {
            if (targetId == null || !clientAddresses.containsKey(targetId)) return;

            byte[] data = message.getBytes();
            InetAddress address = clientAddresses.get(targetId);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, getClientPort(targetId));
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getClientPort(int clientId) {
        return BASE_PORT + clientId;
    }

    public static void main(String[] args) throws IOException {
        int clientId = Integer.parseInt(System.getenv("CLIENT_ID"));
        List<Integer> wishlist = new ArrayList<>();
        for (String s : System.getenv("WISHLIST").split(",")) {
            wishlist.add(Integer.parseInt(s.trim()));
        }

        Map<Integer, InetAddress> clientAddresses = new HashMap<>();
        for (String entry : System.getenv("CLIENT_ADDRESSES").split(",")) {
            String[] parts = entry.split(":");
            clientAddresses.put(Integer.parseInt(parts[1].trim()), InetAddress.getByName(parts[0].trim()));
        }

        Client client = new Client(clientId, wishlist, clientAddresses);
        new Thread(client).start();
    }
}
