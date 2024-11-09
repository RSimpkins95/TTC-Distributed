import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    private final int UDP_PORT = 7000;
    private boolean bAssigned;
    private List<Integer> wishlist = new ArrayList<>();
    private List<Integer> clientList = new ArrayList<>();
    private int clientID;
    private int currentAssignment;
    private DatagramSocket udpSocket;
    public Main() throws SocketException {
        udpSocket = new DatagramSocket();
    }

    public static void main(String[] args) throws SocketException {

        // Input:
        // clientID, choice 1, choice 2, choice 3, choice n, etc...
        // where clientID starts with 1... n
        if(args.length >= 2){
            Main client = new Main();
            client.clientID = Integer.parseInt(args[0]);
            client.currentAssignment = client.clientID;

            for(int i = 1; i < args.length; i++){
                client.wishlist.add(Integer.parseInt(args[i]));
                client.clientList.add(i);
            }

            client.bAssigned = client.wishlist.get(0) == client.currentAssignment;
            client.searchForAssignment();

        }else{
            System.out.println("ERROR: Expected input length greater than 1");
        }
    }

    public void searchForAssignment(){
        try {
            while(!bAssigned){
                String message = "|";
                sendCycleSearch(message);
                waitForMessageReceive();
            }

            sendAssignmentTaken();
            System.out.println(clientID + " assigned: " + currentAssignment);
            udpSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Looks for a cycle by forwarding a message to the next highest available assignment on our list
    private void sendCycleSearch(String receivedMessage) throws IOException {
        StringBuilder messageBuilder = new StringBuilder("SEARCHINGCYCLE|").append(clientID);

        if (receivedMessage != null && !receivedMessage.isEmpty()) {
            messageBuilder.append("|").append(receivedMessage.substring(receivedMessage.indexOf("|") + 1));
        }

        String message = messageBuilder.toString();
        byte[] buffer = message.getBytes();
        InetAddress receiverAddress = InetAddress.getByName("localhost");

        DatagramPacket packetToSend = new DatagramPacket(buffer, buffer.length, receiverAddress, getClientPort(wishlist.get(0)));
        udpSocket.send(packetToSend);
    }

    // We found a cycle, now need to inform everyone in the cycle, update our assignment
    private void sendCycleFoundMessage(String[] tokens) throws IOException {
        bAssigned = true;
        currentAssignment = wishlist.get(0);
        String[] idTokens = Arrays.copyOfRange(tokens, 1, tokens.length);
        String message = "CYCLE";
        byte[] buffer = message.getBytes();
        InetAddress receiverAddress = InetAddress.getByName("localhost");

        for(int i = 0; i < idTokens.length; i++){
            DatagramPacket packetToSend = new DatagramPacket(buffer, buffer.length, receiverAddress, getClientPort(Integer.parseInt(idTokens[i])));
            udpSocket.send(packetToSend);
        }
    }

    // We inform everyone that we have taken the house
    private void sendAssignmentTaken() throws IOException {
        String message = "TAKEN|" + currentAssignment;
        byte[] buffer = message.getBytes();
        InetAddress receiverAddress = InetAddress.getByName("localhost");

        for(int i = 0; i < clientList.size(); i++){
            DatagramPacket packetToSend = new DatagramPacket(buffer, buffer.length, receiverAddress, getClientPort(clientList.get(i)));
            udpSocket.send(packetToSend);
        }
    }

    // General purpose method to handle any messages received
    private void waitForMessageReceive() throws IOException {
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket packetToReceive = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        udpSocket.receive(packetToReceive);
        String response = new String(packetToReceive.getData(), 0, packetToReceive.getLength());
        String[] tokens = response.split("\\|");

        if(tokens.length > 1){
            if(tokens[0].equals("TAKEN")){
                int takenAssignment = Integer.parseInt(tokens[1]);
                receiveAssignmentTakenNotify(takenAssignment);
            }

            if(tokens[0].equals("SEARCHINGCYCLE")){
                receiveCycleSearch(tokens);
            }

            if(tokens[0].equals("CYCLE")){
                receiveCycleFound();
            }
        }
    }

    // Someone else is looking for a cycle
    // Lets check if this was one we initiated
    // If not, lets append and forward
    private void receiveCycleSearch(String[] tokens) throws IOException {
        boolean bFoundMyself = false;
        for(int i = 1; i < tokens.length; i++){
            if(clientID == Integer.parseInt(tokens[i])){
                bFoundMyself = true;
            }
        }

        if(bFoundMyself){
            sendCycleFoundMessage(tokens);
        }else{
            String rebuiltMessage = String.join("|", tokens);
            sendCycleSearch(rebuiltMessage);
        }
    }

    // We were informed we are part of a cycle and take our next best choice
    private void receiveCycleFound(){
        bAssigned = true;
        currentAssignment = wishlist.get(0);
    }

    private void receiveAssignmentTakenNotify(int assignment){
        wishlist.remove(assignment);
    }

    private int getClientPort(int ID){
        return UDP_PORT + ID;
    }
}