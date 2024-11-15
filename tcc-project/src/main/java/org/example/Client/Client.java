package org.example.Client;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client implements Runnable {
    private int clientId;
    private int currentAssignment;
    private volatile boolean bAssigned;
    private CopyOnWriteArrayList<Integer> wishlist;
    private ConcurrentHashMap<Integer, InetAddress> clientAddresses;
    private DatagramSocket socket;
    private static final int BASE_PORT = 5000;

    // Failure detection variables
    private ConcurrentHashMap<Integer, Long> heartbeatTimestamps = new ConcurrentHashMap<>();
    private static final int HEARTBEAT_INTERVAL = 5000; // 5 seconds
    private static final int HEARTBEAT_TIMEOUT = 15000; // 15 seconds

    // Grace period before starting failure detection (in milliseconds)
    private static final int GRACE_PERIOD = 10000; // 10 seconds

    public Client(int clientId, List<Integer> wishlist, Map<Integer, InetAddress> clientAddresses) throws SocketException {
        this.clientId = clientId;
        this.currentAssignment = clientId; // Each client starts with their own resource
        this.bAssigned = false;
        this.wishlist = new CopyOnWriteArrayList<>(wishlist);
        this.clientAddresses = new ConcurrentHashMap<>(clientAddresses);
        this.socket = new DatagramSocket(getClientPort(clientId));
    }

    @Override
    public void run() {
        try {
            // Start heartbeat mechanism
            startHeartbeat();

            // Wait for the grace period before starting failure detection
            Thread.sleep(GRACE_PERIOD);

            // Start failure detection mechanism
            startFailureDetector();

            // Start listening for incoming messages
            new Thread(this::receiveMessages).start();

            // Begin the TTC algorithm
            searchForAssignment();
        } catch (IOException | InterruptedException e) {
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
                // Client is suspected to have failed
                handleClientFailure(id);
            }
        }
    }

    private void handleClientFailure(int failedClientId) {
        System.out.println("Client " + clientId + ": Detected failure of client " + failedClientId);
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
                System.out.println("Client " + clientId + " retains assignment: " + currentAssignment);
                break;
            }

            int topChoice = wishlist.get(0);
            // Start cycle search by pointing to the top choice
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
            System.out.println("Client " + clientId + ": Removed client " + targetId + " from wishlist as it's unavailable.");
            return;
        }

        String message = "SEARCH_CYCLE|" + serializePath(path);
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
                handleHeartbeat(tokens);
                break;
            case "SEARCH_CYCLE":
                handleSearchCycle(tokens);
                break;
            case "CYCLE_FOUND":
                handleCycleFound(tokens);
                break;
            case "ASSIGNMENT":
                handleAssignment(tokens);
                break;
            default:
                System.out.println("Client " + clientId + ": Received unknown message type: " + tokens[0]);
        }
    }

    private void handleHeartbeat(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("Client " + clientId + ": Received malformed HEARTBEAT message.");
            return;
        }
        try {
            int senderId = Integer.parseInt(tokens[1]);
            heartbeatTimestamps.put(senderId, System.currentTimeMillis());
        } catch (NumberFormatException e) {
            System.out.println("Client " + clientId + ": Invalid client ID in HEARTBEAT message.");
        }
    }

    private void handleSearchCycle(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            System.out.println("Client " + clientId + ": Received malformed SEARCH_CYCLE message.");
            return;
        }
        List<Integer> path = deserializePath(tokens[1]);

        if (path.contains(clientId)) {
            // Cycle detected
            System.out.println("Client " + clientId + ": Detected cycle: " + path);
            // Notify all clients in the cycle
            for (int id : path) {
                sendMessage("CYCLE_FOUND|" + serializePath(path), id);
            }
        } else {
            // Continue the search
            path.add(clientId);
            if (wishlist.isEmpty()) {
                System.out.println("Client " + clientId + ": Wishlist is empty, cannot continue cycle search.");
                return;
            }
            int topChoice = wishlist.get(0);
            sendCycleSearch(path, topChoice);
        }
    }

    private void handleCycleFound(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            System.out.println("Client " + clientId + ": Received malformed CYCLE_FOUND message.");
            return;
        }
        List<Integer> cycle = deserializePath(tokens[1]);

        if (cycle.contains(clientId)) {
            // Assign the resource
            assign(currentAssignment);
            // Notify others about the assignment
            for (int id : clientAddresses.keySet()) {
                if (!cycle.contains(id)) {
                    sendMessage("ASSIGNMENT|" + clientId + "|" + currentAssignment, id);
                }
            }
            // Remove assigned clients and their resources
            bAssigned = true;
            for (int id : cycle) {
                if (id != clientId) {
                    clientAddresses.remove(id);
                }
            }
        }
    }

    private void handleAssignment(String[] tokens) {
        if (tokens.length < 3) {
            System.out.println("Client " + clientId + ": Received malformed ASSIGNMENT message.");
            return;
        }
        try {
            int assignedClientId = Integer.parseInt(tokens[1]);
            int assignedResource = Integer.parseInt(tokens[2]);

            // Remove the assigned resource from wishlist
            wishlist.remove(Integer.valueOf(assignedResource));
            // Remove the assigned client from addresses
            clientAddresses.remove(assignedClientId);
            System.out.println("Client " + clientId + ": Updated wishlist after assignment of client " + assignedClientId);

        } catch (NumberFormatException e) {
            System.out.println("Client " + clientId + ": Invalid data in ASSIGNMENT message.");
        }
    }

    // Assigns the current resource to the client
    private void assign(int assignment) {
        this.currentAssignment = assignment;
        this.bAssigned = true;
        System.out.println("Client " + clientId + " assigned resource: " + currentAssignment);
    }

    // Sends a message to a target client
    private void sendMessage(String message, int targetId) throws IOException {
        byte[] data = message.getBytes();
        InetAddress address = clientAddresses.get(targetId);
        if (address == null) {
            System.out.println("Client " + clientId + ": Cannot send message to client " + targetId + " as address is null.");
            return;
        }
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
        int clientId;
        try {
            clientId = Integer.parseInt(clientIdEnv);
        } catch (NumberFormatException e) {
            System.err.println("Invalid CLIENT_ID: " + clientIdEnv);
            System.exit(1);
            return; // Unreachable, but added to satisfy the compiler
        }

        // Read WISHLIST from environment variable
        String wishlistEnv = System.getenv("WISHLIST");
        if (wishlistEnv == null) {
            System.err.println("WISHLIST environment variable not set.");
            System.exit(1);
        }
        List<Integer> wishlist = new ArrayList<>();
        for (String s : wishlistEnv.split(",")) {
            try {
                wishlist.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                System.err.println("Invalid wishlist entry: " + s);
                System.exit(1);
            }
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
            if (parts.length < 2) {
                System.err.println("Invalid CLIENT_ADDRESSES entry: " + entry);
                System.exit(1);
            }
            String hostname = parts[0].trim();
            int id;
            try {
                id = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid client ID in CLIENT_ADDRESSES: " + parts[1].trim());
                System.exit(1);
                return; // Unreachable, but added to satisfy the compiler
            }
            try {
                clientAddresses.put(id, InetAddress.getByName(hostname));
            } catch (UnknownHostException e) {
                System.err.println("Unknown host: " + hostname);
                System.exit(1);
            }
        }

        Client client = new Client(clientId, wishlist, clientAddresses);
        new Thread(client).start();
    }
}
