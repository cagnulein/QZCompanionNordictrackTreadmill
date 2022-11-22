package org.cagnulein.qzcompanionnordictracktreadmill;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.atomic.AtomicBoolean;

import com.cgutman.adblib.AdbCrypto;
import com.cgutman.androidremotedebugger.console.CommandHistory;
import com.cgutman.androidremotedebugger.console.ConsoleBuffer;
import com.cgutman.androidremotedebugger.devconn.DeviceConnection;
import com.cgutman.androidremotedebugger.devconn.DeviceConnectionListener;
import com.cgutman.androidremotedebugger.service.ShellService;

public class AdbRemote implements DeviceConnectionListener {
    private static final String LOG_TAG = "QZ:AdbRemote";
	private static String lastCommand = "";
	private ShellService.ShellServiceBinder binder;

    public void sendCommand(String command) {
		
		lastCommand = command;
		
				/* Create the connection object */
		DeviceConnection conn = binder.createConnection("localhost", 5555);
		
		/* Add this activity as a connection listener */
		binder.addListener(conn, this);
		
		/* Begin the async connection process */
		conn.startConnect();
	}
	
	@Override
	public void notifyConnectionEstablished(DeviceConnection devConn) {
			StringBuilder commandBuffer = new StringBuilder();
		
			commandBuffer.append(lastCommand);
			
			/* Append a newline since it's not included in the command itself */
			commandBuffer.append('\n');
			
			/* Send it to the device */
			devConn.queueCommand(commandBuffer.toString());
	}

	@Override
	public void notifyConnectionFailed(DeviceConnection devConn, Exception e) {
	}

	@Override
	public void notifyStreamFailed(DeviceConnection devConn, Exception e) {
	}

	@Override
	public void notifyStreamClosed(DeviceConnection devConn) {
	}

	@Override
	public AdbCrypto loadAdbCrypto(DeviceConnection devConn) {
		return null;
	}

	@Override
	public boolean canReceiveData() {
		return false;
	}

	@Override
	public void receivedData(DeviceConnection devConn, byte[] data, int offset, int length) {

	}

	@Override
	public boolean isConsole() {
		return false;
	}

	@Override
	public void consoleUpdated(DeviceConnection devConn, ConsoleBuffer console) {

	}
}
