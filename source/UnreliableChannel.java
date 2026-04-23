import java.io.*;
import java.net.*;
import java.util.*;

public class UnreliableChannel
{
    static final int MAX_UDP = 65507;

    static int listenPort;
    static InetAddress serverAddress;
    static int serverPort;

    static double lossProbability = 0.0;
    static double duplicateProbability = 0.0;
    static double delayProbability = 0.0;
    static int maxDelayMs = 400;
    static Long seed = null;

    static DatagramSocket channelSocket;
    static InetAddress clientAddress = null;
    static int clientPort = -1;
    static Random random = new Random();

    public static void main(String[] args)
    {
        try
        {
            for (int i = 0; i < args.length; i++)
            {
                if (args[i].equals("-p") && i + 1 < args.length)
                {
                    listenPort = Integer.parseInt(args[++i]);
                }
                else if (args[i].equals("-sh") && i + 1 < args.length)
                {
                    serverAddress = InetAddress.getByName(args[++i]);
                }
                else if (args[i].equals("-sp") && i + 1 < args.length)
                {
                    serverPort = Integer.parseInt(args[++i]);
                }
                else if (args[i].equals("-loss") && i + 1 < args.length)
                {
                    lossProbability = Double.parseDouble(args[++i]);
                }
                else if (args[i].equals("-dup") && i + 1 < args.length)
                {
                    duplicateProbability = Double.parseDouble(args[++i]);
                }
                else if (args[i].equals("-delay") && i + 1 < args.length)
                {
                    delayProbability = Double.parseDouble(args[++i]);
                }
                else if (args[i].equals("-maxDelay") && i + 1 < args.length)
                {
                    maxDelayMs = Integer.parseInt(args[++i]);
                }
                else if (args[i].equals("-seed") && i + 1 < args.length)
                {
                    seed = Long.valueOf(Long.parseLong(args[++i]));
                }
                else
                {
                    System.out.println("Usage: java UnreliableChannel -p <listenPort> -sh <serverHost> -sp <serverPort>");
                    System.out.println("       [-loss <0.0-1.0>] [-dup <0.0-1.0>] [-delay <0.0-1.0>]");
                    System.out.println("       [-maxDelay <milliseconds>] [-seed <long>]");
                    return;
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Usage: java UnreliableChannel -p <listenPort> -sh <serverHost> -sp <serverPort>");
            System.out.println("       [-loss <0.0-1.0>] [-dup <0.0-1.0>] [-delay <0.0-1.0>]");
            System.out.println("       [-maxDelay <milliseconds>] [-seed <long>]");
            return;
        }

        if (listenPort <= 0 || serverPort <= 0 || serverAddress == null)
        {
            System.out.println("Usage: java UnreliableChannel -p <listenPort> -sh <serverHost> -sp <serverPort>");
            System.out.println("       [-loss <0.0-1.0>] [-dup <0.0-1.0>] [-delay <0.0-1.0>]");
            System.out.println("       [-maxDelay <milliseconds>] [-seed <long>]");
            return;
        }

        if (lossProbability < 0.0 || lossProbability > 1.0
                || duplicateProbability < 0.0 || duplicateProbability > 1.0
                || delayProbability < 0.0 || delayProbability > 1.0
                || maxDelayMs < 0)
        {
            System.out.println("Usage: java UnreliableChannel -p <listenPort> -sh <serverHost> -sp <serverPort>");
            System.out.println("       [-loss <0.0-1.0>] [-dup <0.0-1.0>] [-delay <0.0-1.0>]");
            System.out.println("       [-maxDelay <milliseconds>] [-seed <long>]");
            return;
        }

        if (seed != null)
        {
            random = new Random(seed.longValue());
        }

        try
        {
            channelSocket = new DatagramSocket(listenPort);

            System.out.println("[CHANNEL] Listening on UDP port " + listenPort);
            System.out.println("[CHANNEL] Server endpoint is "
                    + serverAddress.getHostAddress() + ":" + serverPort);
            System.out.println("[CHANNEL] loss=" + lossProbability
                    + " dup=" + duplicateProbability
                    + " delay=" + delayProbability
                    + " maxDelayMs=" + maxDelayMs);

            while (true)
            {
                byte[] buf = new byte[MAX_UDP];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                channelSocket.receive(packet);

                boolean fromServer =
                        packet.getAddress().equals(serverAddress)
                        && packet.getPort() == serverPort;

                if (!fromServer)
                {
                    clientAddress = packet.getAddress();
                    clientPort = packet.getPort();
                }

                if (fromServer && clientAddress == null)
                {
                    System.out.println("[CHANNEL] Dropped server packet because client is unknown.");
                    continue;
                }

                InetAddress targetAddress;
                int targetPort;
                String direction;

                if (fromServer)
                {
                    targetAddress = clientAddress;
                    targetPort = clientPort;
                    direction = "SERVER->CLIENT";
                }
                else
                {
                    targetAddress = serverAddress;
                    targetPort = serverPort;
                    direction = "CLIENT->SERVER";
                }

                byte[] payload = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), payload, 0, packet.getLength());

                forwardWithEffects(payload, targetAddress, targetPort, direction);
            }
        }
        catch (SocketException e)
        {
            System.err.println("[CHANNEL] Socket error: " + e.getMessage());
        }
        catch (IOException e)
        {
            System.err.println("[CHANNEL] I/O error: " + e.getMessage());
        }
    }

    static void forwardWithEffects(
            byte[] payload,
            InetAddress targetAddress,
            int targetPort,
            String direction
    )
    {
        if (random.nextDouble() < lossProbability)
        {
            System.out.println("[CHANNEL] DROP " + direction);
            return;
        }

        long delay = sampleDelay();
        sendPacket(payload, targetAddress, targetPort, direction, "", delay);

        if (random.nextDouble() < duplicateProbability)
        {
            sendPacket(payload, targetAddress, targetPort, direction, " DUP",
                    delay + Math.max(10, maxDelayMs / 4));
        }
    }

    static void sendPacket(
            byte[] payload,
            InetAddress targetAddress,
            int targetPort,
            String direction,
            String suffix,
            long delay
    )
    {
        try
        {
            if (delay > 0)
            {
                Thread.sleep(delay);
            }

            DatagramPacket outgoing =
                    new DatagramPacket(payload, payload.length, targetAddress, targetPort);

            channelSocket.send(outgoing);

            String delayText = "";
            if (delay > 0)
            {
                delayText = " delayed=" + delay + "ms";
            }

            System.out.println("[CHANNEL] FORWARD" + suffix + " " + direction + " "
                    + "to " + targetAddress.getHostAddress() + ":" + targetPort + delayText);
        }
        catch (Exception e)
        {
            System.err.println("[CHANNEL] Forwarding failed: " + e.getMessage());
        }
    }

    static long sampleDelay()
    {
        if (random.nextDouble() >= delayProbability || maxDelayMs <= 0)
        {
            return 0;
        }

        return 1 + random.nextInt(Math.max(1, maxDelayMs));
    }
}
