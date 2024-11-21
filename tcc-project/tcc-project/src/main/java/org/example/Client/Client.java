package org.example.Client;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client implements Runnable {
    private int clientId;
    private volatile boolean active;
    private volatile boolean assigned;
    private int nexti; // Top choice of this client
    private int hi; // Current house held by this client
    private Integer succ; // Successor of this client in cycle detection
    private Set<Integer> children; // Nodes traversed by this client
    private boolean inCycle; // Whether the client is part of a cycle
    private Map<Integer, Integer> prefi; // Preference mapping: house -> client holding it
    private CopyOnWriteArrayList<Integer> wishlist;
    private ConcurrentHashMap<Integer, InetAddress> clientAddresses;
    private DatagramSocket socket;
    private static final int BASE_PORT = 5000;

    public Client(int clientId, List<Integer> wishlist, Map<Integer, InetAddress> clientAddresses) throws SocketException {
        this.clientId = clientId;
        this.assigned = false;
        this.active = true;
        this.inCycle = false;
        this.nexti = wishlist.isEmpty() ? -1 : wishlist.get(0);
        this.hi = -1; // Initially no house assigned
        this.succ = null; // Successor initialized later
        this.children = new HashSet<>();
        this.prefi = new HashMap<>();
        this.wishlist = new CopyOnWriteArrayList<>(wishlist);
        this.clientAddresses = new ConcurrentHashMap<>(clientAddresses);
        this.socket = new DatagramSocket(getClientPort(clientId));
    }

    @Override
    public void run() {
        try {
            new Thread(this::receiveMessages).start();
            while (!assigned) {
                try {
                    oneStage(); // Call to oneStage wrapped in try-catch block
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Error during oneStage execution for Client " + clientId);
                }
                Thread.sleep(1000); // Simulate delay between stages
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void oneStage() throws IOException {
        if (assigned) return;

        succ = nexti; // Set successor to current top choice
        executeCycleDetection();

        if (inCycle) {
            // Assign the house of `nexti` to this client
            hi = nexti;
            assigned = true;
            System.out.println("Client " + clientId + " assigned house: " + hi);

            // Notify others to remove the assigned house
            broadcastMessage("REMOVE|" + hi);

            // If no children, notify parent
            if (children.isEmpty()) {
                sendMessage("OK", null);
            }
        }
    }

    private void executeCycleDetection() throws IOException {
        while (active) {
            // Coin-flip step
            Random random = new Random();
            boolean myCoin = random.nextBoolean();
            boolean succCoin = false;

            if (succ != null) {
                // Ask successor for their coin status
                succCoin = requestCoinStatus(succ);
            }

            if (myCoin && !succCoin) {
                active = false;
                break;
            }

            // Explore step
            if (active) {
                boolean succActive = isActive(succ);

                while (!succActive) {
                    children.add(succ);
                    succ = getNextSuccessor(succ);
                    succActive = isActive(succ);
                }

                if (succ == clientId) {
                    // Cycle detected
                    active = false;
                    break;
                }
            }
        }

        // Notify step
        if (succ == clientId) {
            inCycle = true;
            for (int child : children) {
                sendMessage("CYCLE", child);
            }
        }
    }

    private boolean requestCoinStatus(int targetId) throws IOException {
        sendMessage("COIN_STATUS", targetId);
        return receiveBooleanResponse(targetId);
    }

    private boolean isActive(Integer targetId) throws IOException {
        sendMessage("ACTIVE_STATUS", targetId);
        return receiveBooleanResponse(targetId);
    }

    private Integer getNextSuccessor(Integer targetId) throws IOException {
        sendMessage("NEXT_SUCCESSOR", targetId);
        return receiveIntegerResponse(targetId);
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
            case "CYCLE":
                handleCycle();
                break;
            case "OK":
                handleOk();
                break;
            case "NEXT_STAGE":
                handleNextStage();
                break;
            default:
                System.out.println("Unknown message: " + message);
        }
    }

    private void handleRemove(String[] tokens) {
        if (tokens.length < 2) return;
        int removedHouse = Integer.parseInt(tokens[1]);
        wishlist.remove(Integer.valueOf(removedHouse));
        prefi.remove(removedHouse);
    }

    private void handleCycle() {
        inCycle = true;
        for (int child : children) {
            sendMessage("CYCLE", child);
        }
    }

    private void handleOk() {
        children.removeIf(child -> child == null);
        if (children.isEmpty()) {
            // Notify parent or start next stage if root
            sendMessage("OK", null);
        }
    }

    private void handleNextStage() {
        if (!assigned) {
            active = true;
            if (!wishlist.isEmpty()) {
                nexti = prefi.getOrDefault(wishlist.get(0), -1);
                System.out.println("Client " + clientId + " progressing to next stage with top choice: " + nexti);
            }
        }
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

    private boolean receiveBooleanResponse(int targetId) {
        // Simulate receiving a boolean response
        return new Random().nextBoolean();
    }

    private Integer receiveIntegerResponse(int targetId) {
        // Simulate receiving the next successor
        return targetId + 1; // Placeholder logic
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
