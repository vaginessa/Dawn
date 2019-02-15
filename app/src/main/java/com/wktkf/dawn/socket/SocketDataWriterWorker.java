package com.wktkf.dawn.socket;

import android.support.annotation.NonNull;
import android.util.Log;

import com.wktkf.dawn.IClientPacketWriter;
import com.wktkf.dawn.Session;
import com.wktkf.dawn.SessionManager;
import com.wktkf.dawn.transport.tcp.TCPPacketFactory;
import com.wktkf.dawn.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Date;

public class SocketDataWriterWorker implements Runnable {
	private static final String TAG = "SocketDataWriterWorker";

	private static IClientPacketWriter writer;
	@NonNull private String sessionKey;

	SocketDataWriterWorker(IClientPacketWriter writer, @NonNull String sessionKey) {
		this.writer = writer;
		this.sessionKey = sessionKey;
	}

	@Override
	public void run() {
		final Session session = SessionManager.INSTANCE.getSessionByKey(sessionKey);
		if(session == null) {
			Log.d(TAG, "No session related to " + sessionKey + "for write");
			return;
		}

		session.setBusywrite(true);

		AbstractSelectableChannel channel = session.getChannel();
		if(channel instanceof SocketChannel){
			writeTCP(session);
		}else if(channel instanceof DatagramChannel){
			writeUDP(session);
		} else {
			return;
		}
		session.setBusywrite(false);

		if(session.isAbortingConnection()){
			Log.d(TAG,"removing aborted connection -> " + sessionKey);
			session.getSelectionKey().cancel();

			if(channel instanceof SocketChannel) {
				try {
					SocketChannel socketChannel = (SocketChannel) channel;
					if (socketChannel.isConnected()) {
						socketChannel.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if(channel instanceof DatagramChannel) {
				try {
					DatagramChannel datagramChannel = (DatagramChannel) channel;
					if (datagramChannel.isConnected()) {
						datagramChannel.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			SessionManager.INSTANCE.closeSession(session);
		}
	}

	private void writeUDP(Session session){
		if(!session.hasDataToSend()){
			return;
		}
		DatagramChannel channel = (DatagramChannel) session.getChannel();
		String name = PacketUtil.intToIPAddress(session.getDestIp())+":"+session.getDestPort()+
				"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort();
		byte[] data = session.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		try {
			String str = new String(data);
			Log.d(TAG,"****** data write to server ********");
			Log.d(TAG,str);
			Log.d(TAG,"***** end writing to server *******");
			Log.d(TAG,"writing data to remote UDP: "+name);
			channel.write(buffer);
			Date dt = new Date();
			session.connectionStartTime = dt.getTime();
		}catch(NotYetConnectedException ex2){
			session.setAbortingConnection(true);
			Log.e(TAG,"Error writing to unconnected-UDP server, will abort current connection: "+ex2.getMessage());
		} catch (IOException e) {
			session.setAbortingConnection(true);
			e.printStackTrace();
			Log.e(TAG,"Error writing to UDP server, will abort connection: "+e.getMessage());
		}
	}
	
	private void writeTCP(Session session){
		SocketChannel channel = (SocketChannel) session.getChannel();

		String name = PacketUtil.intToIPAddress(session.getDestIp())+":"+session.getDestPort()+
				"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort();
		
		byte[] data = session.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		
		try {
			Log.d(TAG,"writing TCP data to: " + name);
			int last_limit = buffer.limit();
			for(int i=0; i<6; ++i) {
				if(buffer.position()+2 > last_limit) break;
				buffer.limit(buffer.position()+2);
				channel.write(buffer);
			}
			buffer.limit(last_limit);
			channel.write(buffer);
			//Log.d(TAG,"finished writing data to: "+name);
		} catch (NotYetConnectedException ex) {
			Log.e(TAG,"failed to write to unconnected socket: " + ex.getMessage());
		} catch (IOException e) {
			Log.e(TAG,"Error writing to server: " + e.getMessage());
			
			//close connection with vpn client
			byte[] rstData = TCPPacketFactory.createRstData(
					session.getLastIpHeader(), session.getLastTcpHeader(), 0);
			try {
				writer.write(rstData);
				SocketData socketData = SocketData.getInstance();
				socketData.addData(rstData);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			//remove session
			Log.e(TAG,"failed to write to remote socket, aborting connection");
			session.setAbortingConnection(true);
		}
	}
}
