import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

public class scDriver {

	public static ArrayList<String> networkStatus = new ArrayList<String>();
	DatagramSocket socket = null;
	public static ArrayList<InetAddress> ipArr;
	public static boolean isServer;
	public static int isServerInt;
	public static String serverIP = "192.168.0.14";

	public static Timer timer1 = new Timer();
	public static Timer timer2 = new Timer();
	public static Timer timer3 = new Timer();

	public static TimerTask task1;
	public static TimerTask task2;
	public static TimerTask task3;

	public static ArrayList<InetAddress> initializeNetwork(String ipFile, String myIp) {
		ArrayList<InetAddress> ipArr = new ArrayList<InetAddress>();
		try {
			File file = new File(ipFile);
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String line = br.readLine();
			// add all IPs from host.txt except for the the IP of the machine executing this
			while (line != null) {
				if (!line.contains(myIp)) {
					ipArr.add(InetAddress.getByName(line));
					line = br.readLine();
				} else {
					line = br.readLine();
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			System.out.println("File not found");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("There was an error with the input");
			e.printStackTrace();
		}
		return ipArr;
	}

	public static void updateNetworkList(String data) {
		// The data will come in form "10.0.2.6\n10.0.2.15\n ..."
		String[] dataElements = data.split("\n");
		networkStatus.clear();
		for (String element : dataElements) {
			networkStatus.add(element);
		}
	}

	public static void updateNetworkList(InetAddress incomingIP) {
		if (!networkStatus.contains(incomingIP.getHostAddress())) {
			networkStatus.add(incomingIP.getHostAddress());
		}
	}

	public static byte[] createHeartBeat(String message) {
		ByteArrayOutputStream temp = new ByteArrayOutputStream();
		temp.reset();
		temp.write(1); // 1 for the Data included flag being on
		temp.write(isServerInt); // the server flag, 1 if server and 0 if not server
		temp.write(message.getBytes().length); // length of the message
		temp.write(message.getBytes(), 0, message.getBytes().length);
		return temp.toByteArray();
	}

	public static void sendHeartBeat(InetAddress ip, byte[] data, int port) {
		DatagramSocket Socket;
		try {
			Socket = new DatagramSocket();
			DatagramPacket heartbeat = new DatagramPacket(data, data.length, ip, port);
			Socket.send(heartbeat);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println(ip + " is down\n");
		}
	}

	public static void findServer(String myIp) {
		if (myIp.equals(serverIP)) {
			isServer = true;
			serverIP = myIp;
		} else {
			isServer = false;
		}
		isServerInt = isServer ? 1 : 0;
	}

	public static void main(String[] args) {
		ipArr = initializeNetwork("hosts.txt", args[0]);
		networkStatus.add(args[0]);
		Thread send = new Thread(new Send(args[0]));
		send.start();
		Thread receive = new Thread(new Receive());
		receive.start();
		findServer(args[0]);

		// create the task that will change a machines state to down
		task1 = new IsDown(ipArr.get(0));
		task2 = new IsDown(ipArr.get(1));
		task3 = new IsDown(ipArr.get(2));
		// start the timeout timers for all the IPs in hosts.txt that are not this
		timer1.schedule(task1, 25000);
		timer2.schedule(task2, 25000);
		timer3.schedule(task3, 25000);
	}

	static class Send implements Runnable {

		public Send(String ip) {
		}

		public void run() {
			while (true) {
				// sleep for 5-10 seconds
				Random sleepTime = new Random();
				try {
					Thread.sleep(sleepTime.nextInt(6) * 1000 + 5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// run through the networkStatus list and compile it into one big message
				StringBuilder networkMessage = new StringBuilder();
				for (String status : networkStatus) {
					networkMessage.append(status + "\n");
				}
				byte[] heartbeat = createHeartBeat(networkMessage.toString());
				if (!isServer) {
					try {
						sendHeartBeat(InetAddress.getByName(serverIP), heartbeat, 8067);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
				} else {
					for (InetAddress name : ipArr) {
						sendHeartBeat(name, heartbeat, 8067);
					}
				}
			}
		}
	}

	static class Receive implements Runnable {
		public void run() {
			DatagramSocket socket = null;
			try {
				socket = new DatagramSocket(8067);
				byte[] incomingData = new byte[1024];
				while (true) {
					DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
					socket.receive(incomingPacket);
					// when I get a packet from some IP, reset that IPs timeout timer
					resetTimer(incomingPacket.getAddress());
					// convert the data from binary back into a string
					byte[] data = incomingPacket.getData();
					// If the isServer flag being received is on, and the current machine thinks
					// it's the server, it resets itself to a client.
					if (data[1] == 1) {
						if (isServer) {
							serverIP = incomingPacket.getAddress().getHostAddress();
							isServerInt = 0;
							isServer = false;
							System.out.println();
						}
					}
					System.out.println();
					String dataContent = new String(data, 3, data[2], StandardCharsets.UTF_8);
					if (isServer) {
						updateNetworkList(incomingPacket.getAddress());
					} else {
						updateNetworkList(dataContent);
					}

					// print the status of all the nodes
					for (String status : networkStatus) {
						System.out.println(status + " is on the network");
					}
					System.out.println();
				}
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static class IsDown extends TimerTask {
		private String ip;

		// get the ip of the machine that is down
		public IsDown(InetAddress ip) {
			this.ip = ip.toString().substring(1, ip.toString().length());
		}

		public void run() {
			// update the status to show that the ip is not responding
			System.out.println(ip + " is not responding\n");
			networkStatus.remove(ip);
			if (!networkStatus.contains(serverIP)) {
				serverIP = networkStatus.get(0);
				System.out.println("New server IP is " + serverIP);
				findServer(serverIP);
			}
		}
	}

	static void resetTimer(InetAddress ip) {
		// delete the existing timer and make a new one
		if (ip.equals(ipArr.get(0))) {
			TimerTask task1 = new IsDown(ipArr.get(0));
			timer1.cancel();
			timer1.purge();
			timer1 = new Timer();
			timer1.schedule(task1, 25000);
		} else if (ip.equals(ipArr.get(1))) {
			TimerTask task2 = new IsDown(ipArr.get(1));
			timer2.cancel();
			timer2.purge();
			timer2 = new Timer();
			timer2.schedule(task2, 25000);
		} else if (ip.equals(ipArr.get(2))) {
			TimerTask task3 = new IsDown(ipArr.get(2));
			timer3.cancel();
			timer3.purge();
			timer3 = new Timer();
			timer3.schedule(task3, 25000);
		}
	}

}
// EOF