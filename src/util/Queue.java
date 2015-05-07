package util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.*;

import replica.InputPacket;

@SuppressWarnings("hiding")
public class Queue<InputPacket> extends ConcurrentLinkedQueue<replica.InputPacket> {

	private static final long serialVersionUID = 1L;
	
	private final Lock lock = new ReentrantLock();
	private final Condition notEmpty = lock.newCondition();
	public static boolean pausedFlag = false;

	public Queue() {
		super();
	}
	
	public boolean offer(replica.InputPacket packet) {
		lock.lock();
		try {
			if (packet.msg.startsWith("continue")) {
				notEmpty.signal();
				return true;
			}
			boolean status = super.offer((replica.InputPacket) packet);
			notEmpty.signal();
			return status;
		}
		finally {
			lock.unlock();
		}
	}
	
	public replica.InputPacket poll() {
		lock.lock();
		try {
			while (this.size() == 0 || pausedFlag) {
				try {
					notEmpty.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			replica.InputPacket result = super.poll();
			return result;
		} finally {
			lock.unlock();
		}
	}
}
