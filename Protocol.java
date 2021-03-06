package Protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by jonahschueller on 23.05.17.
 *
 * The Protocol class represents a protocol for network communication.
 * Each Protocol consists of a header of nodes. These nodes can be created with the createNode() Method.
 * For each node must have a unique reference and a Content-interface-implementation.
 * The reference is necessary to differentiate each node. In the content-interface-implementation is defined what to do
 * if the reference is called.
 * At the end of every Protocol will automatically be added a data part.
 *
 * To specify a Protocol a few things need to be set up:
 *  - Header parts and each length in byte (Method: addHeaderItem(byteLen))
 *  - node reference position in header (Method: setKeyPos(pos))
 *  - Each nodes with reference and content-implementation
 *
 *
 * Example Code how to set up a protocol:
 *
 * ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 *
 * Protocol<String> protocol = new Protocol<String>("MyProtocol") {
 *          //In this Method it needs to be defined how to convert a byte array into the generic type of the Protocol
 *          //In this case the generic type is java.lang.String.
            @Override
            protected String convert(byte[] bytes) {
                return new String(bytes);
            }
            //In this Method it needs to be defined how to convert the generic type of the Protocol into a byte array
 *          //In this case the generic type is java.lang.String.
            @Override
            protected byte[] convert(String val) {
                return val.getBytes();
            }
            //In this Method it needs to be defined how to expand the generic type of the Protocol to the keyLength of the protocol
 *          //In this case the generic type is java.lang.String.
            @Override
            protected String expand(String val) {
                return ProtocolHandler.expand(getKeyLen(), val);
            }


            //This Method is used to set up the protocol.
            @Override
            protected void protocolSetup() {
                //Set the keyPosition to 0
                setKeyPos(0);

                // Add 8 byte to the header length
                addHeaderItem(8);

                //add the node "node1"

                createNode("node1", packet ->{
                    System.out.println("Node1 is called, Data: " + new String(packet.getData()));
                })

                //You could add further nodes
                    :
                    :
                    :
                    :

            }
            //The Method evaluate is used to extract the protocol node reference out of a DataPacket
            @Override
            protected String evaluate(DataPacket<String> packet) {
                //In this case the node reference is the fist part of the DataPacket
                return packet.get(0);
            }
        };
 *
 * ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 *
 * This Protocol has a header of 8 bytes and a header, which length is variable (It is the length of the byte array from the DataPacket to send or receive (It happens automatically))
 * Further the Protocol has one Node: "node1"
 *
 *
 *
 *
 */
public abstract class Protocol<T> {

    private final Node<T> ROOT = new Node<T>((T)new Object(), (packet -> {}));
    private static ArrayList<Protocol> protocols = new ArrayList();

    private int keyLen, keyPos;
    private String name;
    private ArrayList<Integer> header;
    private ArrayList<Node<T>> nodes;
    private static final int DATA_SIZE = 4;

    public Protocol(){
        header = new ArrayList<>();
        nodes = new ArrayList<Node<T>>();
        nodes.add(ROOT);
        protocolSetup();
    }

    protected String getGenericName() {
        String className = getClass().getGenericSuperclass().getTypeName();
        if (!className.contains("<")){
            Class superclass = getClass().getSuperclass();
            className = superclass.getGenericSuperclass().getTypeName();
        }
        className = className.split("<", 2)[1];
        className = className.split(">")[0];
        return className;
    }


    public Protocol(String name){
        this();
        this.name = name;
        protocols.add(this);
    }

    /**
     * This Method adds a header part to the protocol.
     * @param len length in byte
     */
    public void addHeaderItem(int len){
        header.add(new Integer(len));
        if (header.size() > keyPos){
            keyLen = header.get(keyPos);
        }
    }

    private byte[] read(int len, InputStream stream) throws IOException{

        byte[] buffer = new byte[0];
        int total = 0;
        int left = len;
        while(total != len){
            byte[] bytes = new byte[left];
            int r = stream.read(bytes);
            if (r == -1)
                continue;

            if (r != left){
                bytes = Arrays.copyOf(bytes, r);
            }
            buffer = addByteArray(buffer, bytes);
            total += r;
        }
        return buffer;
    }

    private byte[] readData(InputStream stream) throws IOException{
        int datalen = ProtocolHandler.intFromByteArray(read(DATA_SIZE, stream));
        return read(datalen, stream);
    }

    protected DataPacket<T> read(Socket socket) throws IOException{
        DataPacket<T> packet = new DataPacket(socket);
        return read(packet, socket.getInputStream());
    }


    protected DataPacket<T> read(DataPacket<T> packet, InputStream stream) throws IOException {

        for (int i = 0; i < header.size(); i++) {
            byte[] buffer = read(header.get(i), stream);
            if (buffer == null){

                try {
                    throw new Exception("Invalid Header! Packet lost");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
            packet.append(convert(buffer));
        }
        packet.setData(readData(stream));

        return packet;
    }

    public void sendPacket(DataPacket<T> packet, Socket socket) throws IOException{
        streamWrite(packet, socket.getOutputStream());
    }

    public void streamWrite(DataPacket<T> packet,OutputStream stream) throws IOException {
        ArrayList<T> header = packet.getHeader();

        for (int i = 0;i < header.size();i++) {

            T val = header.get(i);

            if (i == keyPos){
                val = expand(val);
            }
            //System.out.println(convert(val).length + "  " + getHeader().get(i));
            stream.write(convert(val));

        }
        stream.write(ProtocolHandler.intToByteArray(packet.getData().length));

        stream.write(packet.getData());

    }

    public boolean hasMemory(){

        long free = Runtime.getRuntime().freeMemory();
        long total = Runtime.getRuntime().totalMemory();
        return ((double) free / (double)total > .15);
    }

    private boolean containsRef(T ref){
        if (ref == null){
            return false;
        }

        for (Node node :
                nodes) {
            if(ref.equals(node.getRef())){
                return true;
            }
        }
        return false;
    }

    public void createNode(T ref, Content<T> cn){
        if(containsRef(ref)){
            try {
                throw new KeyAlreadyInUseException(ref);
            } catch (KeyAlreadyInUseException e) {
                e.printStackTrace();
            }
            return;
        }

        Node<T> node = new Node<T>(expand(ref), cn);
        nodes.add(node);
    }

    public ArrayList<Node<T>> getNodes() {
        return nodes;
    }

    public int getKeyLen() {
        return keyLen;
    }

    protected int getKeyPos() {
        return keyPos;
    }

    public void setKeyPos(int pos) {
        keyPos = pos;
    }

    public ArrayList<Integer> getHeader() {
        return header;
    }

    public void print(){
        System.out.println(toString());
    }

    /**
     * In this Method it needs to be defined how to convert a byte array into the generic type of the Protocol.
     * @param bytes
     * @return
     */
    protected abstract T convert(byte[] bytes);

    /**
     * In this Method it needs to be defined how to expand the generic type of the Protocol to the keyLength of the protocol
     * @param val
     * @return
     */
    protected abstract T expand(T val);

    /**
     * In this Method it needs to be defined how to convert the generic type of the Protocol into a byte array
     * @param val
     * @return
     */
    protected abstract byte[] convert(T val);

    /**
     * This Method is used to set up the protocol.
     * (Look at the annotation at the very top)
     */
    protected abstract void protocolSetup();


    protected T evaluate(DataPacket<T> packet){
        return packet.get(keyPos);
    }


    protected Node<T> getROOT() {
        return ROOT;
    }

    public static void appendNodes(Protocol protocol, Node... nodes){
        for (Node node :
                nodes) {
            protocol.nodes.add(node);
        }
    }


    public static Protocol getInstance(String protocol){
        for (Protocol p :
                protocols) {
            if (protocol.toLowerCase().equals(p.name.toLowerCase())){
                return p;
            }
        }

        try {
            throw new Exception("Protocol " + protocol + " does not exist!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Protocol)){
            return false;
        }
        Protocol<T> prot = (Protocol<T>) obj;
        for (int l :
                getHeader()) {
            for (Integer pl: prot.getHeader()) {
                if (l != pl){
                    return false;
                }
            }
        }

        if (getKeyLen() != prot.getKeyLen()){
            return false;
        }

        if (getKeyPos() != prot.getKeyPos()){
            return false;
        }
        return true;
    }


    public static byte[] addByteArray(byte[] one, byte[] two){
        byte[] end = new byte[one.length + two.length];
        System.arraycopy(one, 0, end, 0, one.length);
        System.arraycopy(two, 0, end, one.length, two.length);
        return end;
    }



    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("[");
        for (Node n :
                nodes) {
            sb.append(" - " + n.getRef().toString());
        }
        sb.append(" - ");
        sb.append("]");
        return sb.toString();
    }
}
