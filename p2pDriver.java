import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
public class p2pDriver {
	
	public static ArrayList<String> networkStatus = new ArrayList<String>();
	public static ArrayList<InetAddress> ipArr;
	public static ArrayList<InetAddress> ipSendArr;
	public static Timer timer1 = new Timer();
	public static Timer timer2 = new Timer();
	public static Timer timer3 = new Timer();
	
	public static TimerTask task1;
	public static TimerTask task2;
	public static TimerTask task3;
	
	public static ArrayList<InetAddress> initializeNetwork (String ipFile, String myIp){
		ArrayList<InetAddress> ipArr = new ArrayList<InetAddress>();
		try {	
			File file = new File(ipFile);
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String line = br.readLine();
			//add all IPs from host.txt except for the the IP of the machine executing this
			while (line != null) {
				if (!line.contains(myIp)) {
					ipArr.add(InetAddress.getByName(line));
					line = br.readLine();
				}
				else {
					line = br.readLine();
				}
			}	
			br.close();
		}
		catch (FileNotFoundException e) {
			System.out.println("File not found");
			e.printStackTrace();
		}
		catch (IOException e) {
			System.out.println("There was an error with the input");
			e.printStackTrace();
		}
		return ipArr;
	}
	
	public static void updateIpArr (String data, String myIP) {
		boolean ipAlreadyPresent;
		String[] dataElements = data.split(",");
		for (String element : dataElements) {
			ipAlreadyPresent = false;
			String[] temp = element.split(" ");
			if (element.contains("is down")) {
				ipAlreadyPresent = true;
				for (int i = 0; i < ipSendArr.size(); i++) {
					if (ipSendArr.get(i).toString().contains(temp[0])) {
						ipSendArr.remove(i);
					}
				}
			}
			for (InetAddress ip : ipSendArr) {
				if (ip.toString().contains(temp[0])) {
					ipAlreadyPresent = true;
				}
			}
			if (ipAlreadyPresent == false) {
				if (!temp[0].equals(myIP)) {
					try {
						ipSendArr.add(InetAddress.getByName(temp[0]));
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public static void updateNetworkStatus (String data) {
		boolean statusAlreadyPresent;
		//The data will come in form "10.0.2.6 is up,10.0.2.15 is up, ..."
		String[] dataElements = data.split(",");
		for (String element : dataElements) {
			statusAlreadyPresent = false;
			//grab the IP of the machine
			String[] temp = element.split(" ");
			if (element.contains("is down")) {
				//run through the list and find the entry that says the machine is up then delete it
				for (int i = 0; i < networkStatus.size(); i++) {
					if (networkStatus.get(i).contains(temp[0])) {
						networkStatus.remove(i);
					}
				}
			}
			if (element.contains("is up")) {
				//run through the list and find the entry that says the machine is down then delete it
				for (int i = 0; i < networkStatus.size(); i++) {
					if (networkStatus.get(i).contains(temp[0])) {
						networkStatus.remove(i);
					}
				}
			}
			//if the string is not already in the list, add it
			for (String status : networkStatus) {
				if (status.contains(element) && element.length() > 2) {
					statusAlreadyPresent = true;
				}
			}
			if (statusAlreadyPresent == false) {
				networkStatus.add(element);
			}
		}
	}
	
	public static byte[] createHeartBeat(String message) {
		ByteArrayOutputStream temp = new ByteArrayOutputStream();
		temp.reset();
		temp.write(1); // 1 for the Data included flag being on
		temp.write(0); // 0 for the server flag being on
		temp.write(message.getBytes().length); // length of the message
		temp.write(message.getBytes(), 0, message.getBytes().length); // write the message
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
            e.printStackTrace();
        }
    }
	
	public static void main(String[] args) {
		ipArr = initializeNetwork("hosts.txt", args[0]);
		ipSendArr = initializeNetwork("hosts.txt", args[0]);
		Thread send = new Thread(new Send(args[0]));
		send.start();
		Thread receive = new Thread(new Receive(args[0]));
		receive.start();
		//create the task that will change a machines state to down
		task1 = new IsDown(ipArr.get(0), args[0]);
		task2 = new IsDown(ipArr.get(1), args[0]);
		task3 = new IsDown(ipArr.get(2), args[0]);
		//start the timeout timers for all the IPs in hosts.txt that are not this machine
		timer1.schedule(task1, 25000);
		timer2.schedule(task2, 25000);
		timer3.schedule(task3, 25000);
	}
	
	static class Send implements Runnable {
		private String myIP;
		public Send(String ip) {
			this.myIP = ip;
		}
		public void run() {
			//initially, send that this machine is up to all the IPs on hosts.txt
			String ipMessage = myIP + " is up";
			byte[] data = createHeartBeat(ipMessage);
			for (InetAddress i : ipSendArr) {
				sendHeartBeat(i, data, 8067);
			}
			//go ahead and add this to the overall network status
			networkStatus.add(ipMessage);
			while (true) {
				//sleep for 5-10 seconds
				Random sleepTime = new Random();
				try {
					Thread.sleep(sleepTime.nextInt(6) * 1000 + 5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//run through the networkStatus list and compile it into one big message to send out
				StringBuilder networkMessage = new StringBuilder();
				for (String status : networkStatus) {
					networkMessage.append(status + ",");
				}
				byte[] heartbeat = createHeartBeat(networkMessage.toString());
				for (InetAddress i : ipSendArr) {
					sendHeartBeat(i, heartbeat, 8067);
					System.out.println("Sent heartbeat to: " + i.toString());
				}
			}
		}
	}
	
	static class Receive implements Runnable {
		private String myIP;
		public Receive (String myIP) {
			this.myIP = myIP;
		}
		public void run() {
			DatagramSocket socket = null;
			try {
				socket = new DatagramSocket(8067); 
		        byte[] incomingData = new byte[1024];
		        while (true) {
		        	DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
		        	socket.receive(incomingPacket);
		        	//when I get a packet from some IP, reset that IPs timeout timer
		        	resetTimer(incomingPacket.getAddress(), myIP);
		        	byte[] data = incomingPacket.getData();
		        	//convert the data from binary back into a string
		        	//3 is where the header stuff ends, then data[2] is the length of the message
		        	String dataContent = new String(data, 3, data[2], StandardCharsets.UTF_8);
		        	//send the data to networkStatus and update any stale data
		        	updateNetworkStatus(dataContent);
		        	updateIpArr(dataContent, myIP);
		        	//print the status of all the nodes
		        	for (String status : networkStatus) {
		        		System.out.println(status);
		        	}
		        	System.out.println();
		        } 
			}
		    catch (SocketException e) {
		    	e.printStackTrace();
		    } 
		    catch (IOException e) {
		        e.printStackTrace();
		    } 
		}	
	}
	
	static class IsDown extends TimerTask{
		private String ip;
		private String myIP;
		//get the ip of the machine that is down, the first char in the string is a \ so ignore that
		public IsDown(InetAddress ip, String myIP) {
			this.ip = ip.toString().substring(1, ip.toString().length());
			this.myIP = myIP;
		}
		public void run() {
			//update the status to show that the timer expired
			updateNetworkStatus(ip + " is down");
			updateIpArr(ip + " is down", myIP);
		}
	}
	
	static void resetTimer(InetAddress ip, String myIP) {
		//delete the existing timer and make a new one
		if (ip.equals(ipArr.get(0))) {
			TimerTask task1 = new IsDown(ipArr.get(0), myIP);
			timer1.cancel();
			timer1.purge();
			timer1 = new Timer();
			timer1.schedule(task1, 25000);
		} else if (ip.equals(ipArr.get(1))) {
			TimerTask task2 = new IsDown(ipArr.get(1), myIP);
			timer2.cancel();
			timer2.purge();
			timer2 = new Timer();
			timer2.schedule(task2, 25000);
		} else if (ip.equals(ipArr.get(2))) {
			TimerTask task3 = new IsDown(ipArr.get(2), myIP);
			timer3.cancel();
			timer3.purge();
			timer3 = new Timer();
			timer3.schedule(task3, 25000);
		}	
	}
	
}//EOF
	

