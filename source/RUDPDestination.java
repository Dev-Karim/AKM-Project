import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * RUDPDestination — Reliable UDP Destination (Server)
 *
 * Usage:  java RUDPDestination -p <recvPort>
 *
 * Protocol layout (all multi-byte fields are big-endian):
 *
 *   Byte  0      : packet type
 *                    1 = FILENAME  (data bytes carry the filename as UTF-8)
 *                    2 = DATA      (data bytes carry a file chunk)
 *                    3 = END       (signals end of file; offset field = total bytes)
 *   Bytes 1-4    : offset  (byte offset into the file, or 0 for FILENAME packets)
 *   Bytes 5-8    : length  (number of data bytes that follow the header)
 *   Bytes 9+     : payload (filename string or file data)
 *
 * ACK packets sent back to the source use the same 9-byte header with no payload.
 * The type and offset fields are echoed so the source can identify which packet
 * is being acknowledged.
 *
 * Resources consulted:
 *   - Kurose & Ross, "Computer Networking: A Top-Down Approach", Chapter 3
 *     (Reliable Data Transfer, stop-and-wait protocol)
 *   - Oracle Java SE documentation: DatagramSocket, DatagramPacket, ByteBuffer
 *   - AI-assisted design review (Claude Sonnet 4.6); see AI comparison section
 *     in the project report for details.
 */
public class RUDPDestination
{
    // ── packet type constants ──────────────────────────────────────────────────
    static final byte TYPE_FILENAME = 1;
    static final byte TYPE_DATA     = 2;
    static final byte TYPE_END      = 3;

    // ── protocol constants ─────────────────────────────────────────────────────
    static final int HEADER_SIZE = 9;          // 1 type + 4 offset + 4 length
    static final int MAX_UDP     = 65507;      // maximum UDP payload (bytes)

    // ══════════════════════════════════════════════════════════════════════════
    //  Entry point
    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args)
    {
        int port = parsePort(args);
        if (port < 0)
        {
            System.err.println("Usage: java RUDPDestination -p <recvPort>");
            System.exit(1);
        }

        try (DatagramSocket socket = new DatagramSocket(port))
        {
            System.out.println("[SERVER] Listening on UDP port " + port);

            // Outer loop: server remains running across multiple file transfers.
            while (true)
            {
                receiveOneFile(socket);
            }

        }
        catch (SocketException e)
        {
            System.err.println("[ERROR] Cannot bind to port: " + e.getMessage());
        }
        catch (IOException e)
        {
            System.err.println("[ERROR] I/O failure: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  receiveOneFile
    //  Handles a complete send-of-one-file from the client.
    //  Phases:  (1) receive FILENAME  →  (2) receive DATA chunks  →  (3) END
    // ══════════════════════════════════════════════════════════════════════════
    static void receiveOneFile(DatagramSocket socket) throws IOException
    {
        byte[] buf = new byte[MAX_UDP];

        // ── Phase 1: wait for the FILENAME packet ──────────────────────────────
        String       fileName   = null;
        InetAddress  clientAddr = null;
        int          clientPort = -1;

        while (fileName == null)
        {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);

            if (pkt.getLength() < HEADER_SIZE) continue;   // malformed — ignore

            if (buf[0] != TYPE_FILENAME) continue;          // not a FILENAME packet

            clientAddr = pkt.getAddress();
            clientPort = pkt.getPort();

            int nameLen = readInt(buf, 5);                  // length field
            fileName    = new String(buf, HEADER_SIZE, nameLen, "UTF-8");

            // Requirement 1: the server displays the received packet content
            // together with both sender and receiver endpoint information.
            logPacketDetails(socket, pkt, TYPE_FILENAME, 0, nameLen);
            System.out.println("[SERVER] Incoming file transfer: \"" + fileName + "\"");
            sendAck(socket, clientAddr, clientPort, TYPE_FILENAME, 0);
        }

        // Derive the output path: append "-copy" before the file extension.
        String outputName = buildCopyName(fileName);

        // ── Phase 2 & 3: receive DATA chunks, then the END marker ─────────────
        int nextExpectedOffset = 0;     // tracks contiguous bytes received so far

        try (FileOutputStream fos = new FileOutputStream(outputName))
        {
            boolean done = false;

            while (!done)
            {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                int pktLen = pkt.getLength();
                if (pktLen < HEADER_SIZE) continue;         // malformed — ignore

                byte type    = buf[0];
                int  offset  = readInt(buf, 1);
                int  dataLen = readInt(buf, 5);

                // Requirement 1: every received packet is logged with sender IP,
                // receiver IP, and a preview of its message/content.
                logPacketDetails(socket, pkt, type, offset, dataLen);

                // ── Duplicate FILENAME (our ACK was lost) ───────────────────────
                if (type == TYPE_FILENAME)
                {
                    sendAck(socket, clientAddr, clientPort, TYPE_FILENAME, 0);
                    continue;
                }

                // ── DATA packet ─────────────────────────────────────────────────
                if (type == TYPE_DATA)
                {
                    if (offset == nextExpectedOffset)
                    {
                        // Expected in-order packet → accept.
                        fos.write(buf, HEADER_SIZE, dataLen);
                        nextExpectedOffset += dataLen;
                        System.out.println("[DATA RECEPTION]: " + offset
                                + " | " + dataLen + " | OK");
                        sendAck(socket, clientAddr, clientPort, TYPE_DATA, offset);

                    }
                    else if (offset < nextExpectedOffset)
                    {
                        // Duplicate packet (client re-sent because our ACK was lost).
                        // We must still ACK so the client can advance past this packet.
                        System.out.println("[DATA RECEPTION]: " + offset
                                + " | " + dataLen + " | DISCARDED");
                        sendAck(socket, clientAddr, clientPort, TYPE_DATA, offset);

                    }
                    else
                    {
                        // offset > nextExpectedOffset: gap — should not occur in
                        // stop-and-wait, but guard against it to avoid corruption.
                        System.out.println("[DATA RECEPTION]: " + offset
                                + " | " + dataLen + " | DISCARDED (unexpected future offset)");
                    }
                    continue;
                }

                // ── END packet ──────────────────────────────────────────────────
                if (type == TYPE_END)
                {
                    int totalBytes = offset;   // sender stores total size in offset field
                    System.out.println("[SERVER] END packet received. "
                            + "Expected total: " + totalBytes
                            + " bytes | Received: " + nextExpectedOffset + " bytes.");
                    sendAck(socket, clientAddr, clientPort, TYPE_END, totalBytes);
                    done = true;
                }
            }
        }

        System.out.println("[COMPLETE] \"" + fileName
                + "\" saved as \"" + outputName + "\" ("
                + nextExpectedOffset + " bytes).");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  sendAck — builds and transmits a 9-byte ACK datagram back to the source.
    // ══════════════════════════════════════════════════════════════════════════
    static void sendAck(DatagramSocket socket, InetAddress addr, int port,
                        byte type, int offset) throws IOException
    {
        byte[] ack = new byte[HEADER_SIZE];
        ack[0] = type;
        writeInt(ack, 1, offset);
        writeInt(ack, 5, 0);        // length field is unused in ACKs

        socket.send(new DatagramPacket(ack, ack.length, addr, port));

        // Human-readable ACK confirmation on the server console.
        if (type == TYPE_DATA)
        {
            System.out.println("[ACK SENT]: DATA offset=" + offset);
        }
        else if (type == TYPE_FILENAME)
        {
            System.out.println("[ACK SENT]: FILENAME acknowledged");
        }
        else if (type == TYPE_END)
        {
            System.out.println("[ACK SENT]: END acknowledged (total=" + offset + " bytes)");
        }
    }

    static void logPacketDetails(
            DatagramSocket socket,
            DatagramPacket pkt,
            byte type,
            int offset,
            int dataLen
    ) throws IOException
    {
        String sender = pkt.getAddress().getHostAddress() + ":" + pkt.getPort();
        String receiver = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
        String contentPreview = previewPayload(pkt.getData(), pkt.getOffset(), pkt.getLength(), type, dataLen);

        System.out.println("[PACKET] type=" + typeName(type)
                + " offset=" + offset
                + " length=" + dataLen
                + " sender=" + sender
                + " receiver=" + receiver
                + " content=" + contentPreview);
    }

    static String previewPayload(byte[] buf, int packetOffset, int packetLength, byte type, int dataLen)
            throws UnsupportedEncodingException
    {
        if (packetLength < HEADER_SIZE)
        {
            return "\"<malformed>\"";
        }

        if (type == TYPE_FILENAME)
        {
            return "\"" + new String(buf, HEADER_SIZE, dataLen, "UTF-8") + "\"";
        }

        if (type == TYPE_END)
        {
            return "\"<end-of-file>\"";
        }

        int previewLen = Math.min(dataLen, 24);
        boolean printable = true;
        for (int i = 0; i < previewLen; i++)
        {
            int value = buf[HEADER_SIZE + i] & 0xff;
            if (value < 32 || value > 126)
            {
                printable = false;
                break;
            }
        }

        if (printable && previewLen > 0)
        {
            String preview = new String(buf, HEADER_SIZE, previewLen, "UTF-8");
            return "\"" + preview + (dataLen > previewLen ? "...\"" : "\"");
        }

        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < previewLen; i++)
        {
            if (i > 0) hex.append(' ');
            hex.append(String.format("%02X", buf[HEADER_SIZE + i] & 0xff));
        }
        if (dataLen > previewLen)
        {
            hex.append(" ...");
        }
        return "\"0x" + hex + "\"";
    }

    static String typeName(byte type)
    {
        if (type == TYPE_FILENAME) return "FILENAME";
        if (type == TYPE_DATA) return "DATA";
        if (type == TYPE_END) return "END";
        return "UNKNOWN(" + type + ")";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reads a big-endian 32-bit integer from buf starting at index i.
     */
    static int readInt(byte[] buf, int i)
    {
        return ByteBuffer.wrap(buf, i, 4).getInt();
    }

    /**
     * Writes a big-endian 32-bit integer into buf starting at index i.
     */
    static void writeInt(byte[] buf, int i, int value)
    {
        ByteBuffer.wrap(buf, i, 4).putInt(value);
    }

    /**
     * Inserts "-copy" before the file extension (or at the end if no extension).
     * Examples:  "report.pdf" → "report-copy.pdf"
     *            "data"       → "data-copy"
     */
    static String buildCopyName(String name)
    {
        int dot = name.lastIndexOf('.');
        if (dot < 0)
        {
            return name + "-copy";
        }
        return name.substring(0, dot) + "-copy" + name.substring(dot);
    }

    /**
     * Parses the -p <port> flag from the command-line arguments.
     * Returns -1 if the flag is missing or the value is not an integer.
     */
    static int parsePort(String[] args)
    {
        for (int i = 0; i < args.length - 1; i++)
        {
            if (args[i].equals("-p"))
            {
                try
                {
                    return Integer.parseInt(args[i + 1]);
                }
                catch (NumberFormatException e)
                {
                    return -1;
                }
            }
        }
        return -1;
    }
}
