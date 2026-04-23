import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * RUDPSource — minimal stop-and-wait sender used for integration testing.
 *
 * Usage:
 *   java RUDPSource -f <filePath> -h <channelHost> -p <channelPort>
 *                   [-chunk <bytes>] [-timeout <milliseconds>]
 */
public class RUDPSource
{
    static final byte TYPE_FILENAME = 1;
    static final byte TYPE_DATA     = 2;
    static final byte TYPE_END      = 3;

    static final int HEADER_SIZE = 9;

    public static void main(String[] args)
    {
        Config config = Config.parse(args);
        if (config == null)
        {
            printUsage();
            System.exit(1);
        }

        File inputFile = new File(config.filePath);
        if (!inputFile.isFile())
        {
            System.err.println("[CLIENT] File not found: " + config.filePath);
            System.exit(1);
        }

        try (DatagramSocket socket = new DatagramSocket())
        {
            socket.setSoTimeout(config.timeoutMs);

            InetAddress channelAddress = InetAddress.getByName(config.host);
            String baseName = inputFile.getName();

            sendWithRetransmission(socket, channelAddress, config.port,
                    buildPacket(TYPE_FILENAME, 0, baseName.getBytes(StandardCharsets.UTF_8)),
                    TYPE_FILENAME, 0, "[CLIENT] Sending FILENAME \"" + baseName + "\"");

            int totalBytes = 0;
            int offset = 0;

            try (FileInputStream fis = new FileInputStream(inputFile))
            {
                byte[] chunk = new byte[config.chunkSize];
                int bytesRead;

                while ((bytesRead = fis.read(chunk)) != -1)
                {
                    // Requirement 1: packet payload size varies with the actual
                    // bytes read from the file, so packets are not fixed-size.
                    byte[] payload = new byte[bytesRead];
                    System.arraycopy(chunk, 0, payload, 0, bytesRead);

                    System.out.println("[CLIENT] Sending DATA offset=" + offset + " length=" + bytesRead);
                    sendWithRetransmission(socket, channelAddress, config.port,
                            buildPacket(TYPE_DATA, offset, payload),
                            TYPE_DATA, offset, null);

                    offset += bytesRead;
                    totalBytes += bytesRead;
                }
            }

            System.out.println("[CLIENT] Sending END totalBytes=" + totalBytes);
            sendWithRetransmission(socket, channelAddress, config.port,
                    buildPacket(TYPE_END, totalBytes, new byte[0]),
                    TYPE_END, totalBytes, null);

            System.out.println("[CLIENT] Transfer complete for \"" + baseName + "\" (" + totalBytes + " bytes).");

        }
        catch (IOException e)
        {
            // Requirement 6 / 7: sender-side socket and I/O failures are logged
            // clearly for debugging during integration tests.
            System.err.println("[CLIENT] I/O error: " + e.getMessage());
        }
    }

    static void sendWithRetransmission(
            DatagramSocket socket,
            InetAddress host,
            int port,
            byte[] packetBytes,
            byte expectedType,
            int expectedOffset,
            String intro
    ) throws IOException
    {
        if (intro != null)
        {
            System.out.println(intro);
        }

        DatagramPacket outbound = new DatagramPacket(packetBytes, packetBytes.length, host, port);
        byte[] ackBuf = new byte[HEADER_SIZE];

        while (true)
        {
            socket.send(outbound);
            System.out.println("[CLIENT] SENT " + describe(packetBytes));

            try
            {
                DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                socket.receive(ackPacket);

                if (ackPacket.getLength() < HEADER_SIZE)
                {
                    System.out.println("[CLIENT] Ignoring malformed ACK.");
                    continue;
                }

                byte ackType = ackBuf[0];
                int ackOffset = readInt(ackBuf, 1);

                if (ackType == expectedType && ackOffset == expectedOffset)
                {
                    System.out.println("[CLIENT] ACK received for " + typeName(ackType)
                            + " offset=" + ackOffset);
                    return;
                }

                System.out.println("[CLIENT] Unexpected ACK ignored: type="
                        + typeName(ackType) + " offset=" + ackOffset);

            }
            catch (SocketTimeoutException e)
            {
                System.out.println("[CLIENT] TIMEOUT -> retransmitting " + typeName(expectedType)
                        + " offset=" + expectedOffset);
            }
        }
    }

    static byte[] buildPacket(byte type, int offset, byte[] payload)
    {
        byte[] packet = new byte[HEADER_SIZE + payload.length];
        packet[0] = type;
        writeInt(packet, 1, offset);
        writeInt(packet, 5, payload.length);
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.length);
        return packet;
    }

    static int readInt(byte[] buf, int index)
    {
        return ByteBuffer.wrap(buf, index, 4).getInt();
    }

    static void writeInt(byte[] buf, int index, int value)
    {
        ByteBuffer.wrap(buf, index, 4).putInt(value);
    }

    static String describe(byte[] packetBytes)
    {
        if (packetBytes.length < HEADER_SIZE)
        {
            return "MALFORMED";
        }

        return typeName(packetBytes[0])
                + " offset=" + readInt(packetBytes, 1)
                + " length=" + readInt(packetBytes, 5)
                + " bytes=" + packetBytes.length;
    }

    static String typeName(byte type)
    {
        if (type == TYPE_FILENAME) return "FILENAME";
        if (type == TYPE_DATA) return "DATA";
        if (type == TYPE_END) return "END";
        return "UNKNOWN(" + type + ")";
    }

    static void printUsage()
    {
        System.err.println("Usage: java RUDPSource -f <filePath> -h <channelHost> -p <channelPort>");
        System.err.println("       [-chunk <bytes>] [-timeout <milliseconds>]");
    }

    static final class Config
    {
        final String filePath;
        final String host;
        final int port;
        final int chunkSize;
        final int timeoutMs;

        Config(String filePath, String host, int port, int chunkSize, int timeoutMs)
        {
            this.filePath = filePath;
            this.host = host;
            this.port = port;
            this.chunkSize = chunkSize;
            this.timeoutMs = timeoutMs;
        }

        static Config parse(String[] args)
        {
            String filePath = null;
            String host = null;
            Integer port = null;
            int chunkSize = 1024;
            int timeoutMs = 500;

            for (int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                try
                {
                    if ("-f".equals(arg) && i + 1 < args.length)
                    {
                        filePath = args[++i];
                    }
                    else if ("-h".equals(arg) && i + 1 < args.length)
                    {
                        host = args[++i];
                    }
                    else if ("-p".equals(arg) && i + 1 < args.length)
                    {
                        port = Integer.parseInt(args[++i]);
                    }
                    else if ("-chunk".equals(arg) && i + 1 < args.length)
                    {
                        chunkSize = Integer.parseInt(args[++i]);
                    }
                    else if ("-timeout".equals(arg) && i + 1 < args.length)
                    {
                        timeoutMs = Integer.parseInt(args[++i]);
                    }
                    else
                    {
                        return null;
                    }
                }
                catch (NumberFormatException e)
                {
                    return null;
                }
            }

            if (filePath == null || host == null || port == null || chunkSize <= 0 || timeoutMs <= 0)
            {
                return null;
            }

            return new Config(filePath, host, port, chunkSize, timeoutMs);
        }
    }
}
