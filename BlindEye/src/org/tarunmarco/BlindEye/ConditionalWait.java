package org.tarunmarco.BlindEye;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionalWait {
	final Lock lock = new ReentrantLock();
	final Condition notFull  = lock.newCondition();
	final long timeToWakeup = 1000;
	volatile private boolean proceed  = false;
	
	public boolean locationProviderProceed() throws InterruptedException{
		boolean returnIfProceed;
		lock.lock();
		try{
			while(proceed == false)
				notFull.await(timeToWakeup, TimeUnit.MILLISECONDS);
			returnIfProceed = proceed;		
		} finally{
			lock.unlock();
		}
		return returnIfProceed;
	}
	public void setRunStatus(boolean shouldRun)	{
		lock.lock();
		proceed = shouldRun;
		lock.unlock();		
	}
	  
}
