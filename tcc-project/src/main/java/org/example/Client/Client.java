package org.example.Client;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Client implements Runnable {
    private int clientId;
    private int currentAssignment;
    private boolean bAssigned;
    private List<Integer> wishlist;
    private Map<Integer, InetAddress> clientAddresses;
    private DatagramSocket socket;
    private static final int BASE_PORT = 5000;

    // Failure detection variables
    private Map<Integer, Long> heartbeatTimestamps = new ConcurrentHashMap<>();
    private static final int HEARTBEAT_INTERVAL = 2000; // in milliseconds
    private static final int HEARTBEAT_TIMEOUT = 5000;  // in milliseconds

    public Client(int clientId, List<Integer> wishlist, Map<Integer, InetAddress> clientAddresses) throws SocketException {
        this.clientId = clientId;
        this.currentAssignment = clientId; // Initially assigned to own ID
        this.bAssigned = false;
        this.wishlist = Collections.synchronizedList(new ArrayList<>(wishlist));
        this.clientAddresses = clientAddresses;
        this.socket = new DatagramSocket(getClientPort(clientId));
    }

    @Override
    public void run() {
        try {
            // Start heartbeat and failure detection mechanisms
            startHeartbeat();
            startFailureDetector();

            // Start listening for incoming messages
            new Thread(this::receiveMessages).start();

            // Begin the TTC algorithm
            searchForAssignment();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Heartbeat mechanism to detect client failures
    private void startHeartbeat() {
        new Thread(() -> {
            while (!bAssigned) {
                try {
                    sendHeartbeat();
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendHeartbeat() throws IOException {
        String message = "HEARTBEAT|" + clientId;
        for (int id : clientAddresses.keySet()) {
            if (id != clientId) {
                sendMessage(message, id);
            }
        }
    }

    // Failure detection based on heartbeat timeouts
    private void startFailureDetector() {
        new Thread(() -> {
            while (!bAssigned) {
                try {
                    checkForFailures();
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void checkForFailures() {
        long currentTime = System.currentTimeMillis();
        for (Integer id : clientAddresses.keySet()) {
            if (id == clientId) continue;
            long lastHeartbeat = heartbeatTimestamps.getOrDefault(id, 0L);
            if (currentTime - lastHeartbeat > HEARTBEAT_TIMEOUT) {
                // main.java.Client is suspected to have failed
                handleClientFailure(id);
            }
        }
    }

    private void handleClientFailure(int failedClientId) {
        System.out.println("main.java.Client " + clientId + ": Detected failure of client " + failedClientId);
        // Remove the failed client from the wishlist
        wishlist.remove(Integer.valueOf(failedClientId));
        // Remove the failed client's address
        clientAddresses.remove(failedClientId);
    }

    // The main TTC algorithm logic
    private void searchForAssignment() throws IOException {
        while (!bAssigned) {
            if (wishlist.isEmpty()) {
                // No more preferences; retain current assignment
                bAssigned = true;
                System.out.println("main.java.Client " + clientId + " retains assignment: " + currentAssignment);
                break;
            }

            int topChoice = wishlist.get(0);
            sendCycleSearch(new ArrayList<>(Arrays.asList(clientId)), topChoice);

            // Wait before the next attempt to simulate processing time
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Sends a cycle search message to the target client
    private void sendCycleSearch(List<Integer> path, int targetId) throws IOException {
        if (!clientAddresses.containsKey(targetId)) {
            // Target client is not available; remove from wishlist
            wishlist.remove(0);
            return;
        }

        String message = "SEARCHINGCYCLE|" + serializePath(path);
        sendMessage(message, targetId);
    }

    // Listens for incoming messages and processes them
    private void receiveMessages() {
        byte[] buffer = new byte[1024];
        while (!bAssigned) {
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

    // Handles incoming messages based on their type
    private void handleMessage(String message) throws IOException {
        String[] tokens = message.split("\\|");
        switch (tokens[0]) {
            case "HEARTBEAT":
                int senderId = Integer.parseInt(tokens[1]);
                heartbeatTimestamps.put(senderId, System.currentTimeMillis());
                break;
            case "SEARCHINGCYCLE":
                List<Integer> path = deserializePath(tokens[1]);
                if (path.contains(clientId)) {
                    // Cycle detected
                    List<Integer> cycle = path.subList(path.indexOf(clientId), path.size());
                    notifyCycleFound(cycle);
                } else {
                    path.add(clientId);
                    if (wishlist.isEmpty()) return;
                    int topChoice = wishlist.get(0);
                    sendCycleSearch(path, topChoice);
                }
                break;
            case "TAKEN":
                int takenAssignment = Integer.parseInt(tokens[1]);
                wishlist.remove(Integer.valueOf(takenAssignment));
                break;
            case "CYCLE":
                assign(wishlist.get(0));
                break;
        }
    }

    // Notifies clients involved in a detected cycle
    private void notifyCycleFound(List<Integer> cycle) throws IOException {
        for (int id : cycle) {
            sendMessage("CYCLE", id);
        }
        // Inform other clients about the taken assignments
        for (int id : clientAddresses.keySet()) {
            if (!cycle.contains(id)) {
                sendMessage("TAKEN|" + wishlist.get(0), id);
            }
        }
    }

    // Assigns the top choice from the wishlist to the client
    private void assign(int assignment) {
        this.currentAssignment = assignment;
        this.bAssigned = true;
        System.out.println("main.java.Client " + clientId + " assigned: " + currentAssignment);
    }

    // Sends a message to a target client
    private void sendMessage(String message, int targetId) throws IOException {
        byte[] data = message.getBytes();
        InetAddress address = clientAddresses.get(targetId);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, getClientPort(targetId));
        socket.send(packet);
    }

    // Serializes a list of integers into a comma-separated string
    private String serializePath(List<Integer> path) {
        return String.join(",", path.stream().map(Object::toString).toArray(String[]::new));
    }

    // Deserializes a comma-separated string into a list of integers
    private List<Integer> deserializePath(String data) {
        List<Integer> path = new ArrayList<>();
        for (String s : data.split(",")) {
            path.add(Integer.parseInt(s));
        }
        return path;
    }

    // Calculates the client's port number based on its ID
    private int getClientPort(int clientId) {
        return BASE_PORT + clientId;
    }

    // Main method to run the client application
    public static void main(String[] args) throws IOException {
        // Read CLIENT_ID from environment variable
        String clientIdEnv = System.getenv("CLIENT_ID");
        if (clientIdEnv == null) {
            System.err.println("CLIENT_ID environment variable not set.");
            System.exit(1);
        }
        int clientId = Integer.parseInt(clientIdEnv);

        // Read WISHLIST from environment variable
        String wishlistEnv = System.getenv("WISHLIST");
        if (wishlistEnv == null) {
            System.err.println("WISHLIST environment variable not set.");
            System.exit(1);
        }
        List<Integer> wishlist = new ArrayList<>();
        for (String s : wishlistEnv.split(",")) {
            wishlist.add(Integer.parseInt(s.trim()));
        }

        // Read CLIENT_ADDRESSES from environment variable
        String clientAddressesEnv = System.getenv("CLIENT_ADDRESSES");
        if (clientAddressesEnv == null) {
            System.err.println("CLIENT_ADDRESSES environment variable not set.");
            System.exit(1);
        }
        Map<Integer, InetAddress> clientAddresses = new HashMap<>();
        for (String entry : clientAddressesEnv.split(",")) {
            String[] parts = entry.split(":");
            String hostname = parts[0].trim();
            int id = Integer.parseInt(parts[1].trim());
            clientAddresses.put(id, InetAddress.getByName(hostname));
        }

        Client client = new Client(clientId, wishlist, clientAddresses);
        new Thread(client).start();
    }
}
